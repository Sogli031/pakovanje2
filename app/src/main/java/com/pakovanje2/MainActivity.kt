package com.pakovanje2

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MM_TO_M = 1000.0
private const val PALLET_CLEARANCE_MM = 50.0
private const val MATERIAL_PLACEHOLDER = "Izaberite"
private const val WEIGHT_TOLERANCE = 0.10
private const val WEIGHT_PRECISION = 10

private val MATERIAL_DENSITIES = mapOf(
    "cu" to 8960.0,
    "cuzn10" to 8800.0,
    "cuzn15" to 8686.0,
    "cuzn20" to 8530.0,
    "cuzn30" to 8403.0,
    "cuzn37" to 8285.0
)

private val MATERIAL_ALIASES = mapOf(
    "10" to "cuzn10",
    "15" to "cuzn15",
    "20" to "cuzn20",
    "30" to "cuzn30",
    "37" to "cuzn37"
)

private data class RollDimensions(
    val outerDiameterMm: Double,
    val coreDiameterMm: Double,
    val thicknessMm: Double,
    val widthMm: Double
) {
    companion object {
        fun fromRadialThickness(
            radialThicknessMm: Double,
            coreDiameterMm: Double,
            thicknessMm: Double,
            widthMm: Double
        ): RollDimensions {
            val outerDiameter = coreDiameterMm + (2.0 * radialThicknessMm)
            return RollDimensions(outerDiameter, coreDiameterMm, thicknessMm, widthMm)
        }
    }

    val outerRadiusMm: Double
        get() = outerDiameterMm / 2.0

    val coreRadiusMm: Double
        get() = coreDiameterMm / 2.0

    val radialThicknessMm: Double
        get() = (outerDiameterMm - coreDiameterMm) / 2.0

    fun validate() {
        if (thicknessMm <= 0.0) {
            throw IllegalArgumentException("Debljina materijala mora biti pozitivna vrednost.")
        }
        if (widthMm <= 0.0) {
            throw IllegalArgumentException("Širina trake mora biti pozitivna vrednost.")
        }
        if (outerRadiusMm <= coreRadiusMm) {
            throw IllegalArgumentException("Spoljašnji prečnik mora biti veći od unutrasnjeg prečnika doboša.")
        }
    }
}

private data class RollResult(
    val lengthM: Double,
    val weightKg: Double
)

private data class CoilEntry(
    val material: String,
    val dimensions: RollDimensions,
    val coilCount: Int,
    val roll: RollResult
) {
    val totalWeightKg: Double
        get() = roll.weightKg * coilCount
}

private data class PalletLoad(
    val index: Int,
    val coilCount: Int,
    val totalHeightMm: Double,
    val maxHeightMm: Double,
    val totalWeightKg: Double,
    val contents: List<Pair<Int, Int>>
)

private data class CoilItem(
    val widthMm: Double,
    val weightKg: Double,
    val entryIndex: Int
)

private data class PalletState(
    var coilCount: Int,
    var stackHeightMm: Double,
    var totalWeightKg: Double,
    val items: MutableList<CoilItem>
)

private data class TargetSelection(
    val items: List<CoilItem>,
    val totalWeightKg: Double,
    val inTolerance: Boolean
)

class MainActivity : AppCompatActivity() {
    private lateinit var messageLabel: TextView
    private lateinit var recommendedPalletText: TextView
    private lateinit var entriesHeader: TextView
    private lateinit var entriesContainer: LinearLayout
    private lateinit var resultsHeader: TextView
    private lateinit var resultsContainer: LinearLayout

    private val entries = mutableListOf<CoilEntry>()
    private var recommendedPalletSizeCm: Int? = null
    private lateinit var palletChoice: ChoiceGroup
    private lateinit var materialChoice: ChoiceGroup

    // Grupa dugmadi za izbor (jedno izabrano); opcioni prvi red preko cele sirine, ostalo po 3 u redu.
    private inner class ChoiceGroup(
        container: LinearLayout,
        labels: List<String>,
        fullWidthFirst: Boolean,
        initial: Int
    ) {
        private val buttons = mutableListOf<MaterialButton>()
        var selected = -1
            private set

        init {
            val density = resources.displayMetrics.density
            val gap = (6 * density).roundToInt()
            container.removeAllViews()

            fun newButton(index: Int, label: String) =
                MaterialButton(
                    container.context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = label
                    isAllCaps = false
                    maxLines = 1
                    minHeight = (48 * density).roundToInt()
                    cornerRadius = (12 * density).roundToInt()
                    strokeWidth = (1 * density).roundToInt()
                    setOnClickListener { select(index) }
                    buttons.add(this)
                }

            fun row() = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                container.addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = gap }
                )
            }

            var offset = 0
            if (fullWidthFirst) {
                row().addView(
                    newButton(0, labels[0]),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                offset = 1
            }
            labels.drop(offset).chunked(3).forEachIndexed { rowIndex, chunk ->
                val rowLayout = row()
                chunk.forEachIndexed { i, label ->
                    rowLayout.addView(
                        newButton(offset + rowIndex * 3 + i, label),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { if (i > 0) marginStart = gap }
                    )
                }
            }
            select(initial)
        }

        fun select(position: Int) {
            selected = position
            val accent = ContextCompat.getColor(this@MainActivity, R.color.accent)
            val normal = ContextCompat.getColor(this@MainActivity, R.color.surface_light)
            buttons.forEachIndexed { i, b ->
                val isSelected = i == position
                b.setBackgroundColor(if (isSelected) accent else normal)
                b.setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (isSelected) R.color.background else R.color.text_primary
                    )
                )
                b.strokeColor = ColorStateList.valueOf(if (isSelected) accent else normal)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val palletHeightInput = findViewById<TextInputEditText>(R.id.palletHeightInput)
        val separatorInput = findViewById<TextInputEditText>(R.id.separatorInput)
        val maxPalletWeightInput = findViewById<TextInputEditText>(R.id.maxPalletWeightInput)
        val targetWeightInput = findViewById<TextInputEditText>(R.id.targetWeightInput)
        val palletSizeContainer = findViewById<LinearLayout>(R.id.palletSizeContainer)

        val radialThicknessInput = findViewById<TextInputEditText>(R.id.radialThicknessInput)
        val coreDiameterInput = findViewById<TextInputEditText>(R.id.coreDiameterInput)
        val materialThicknessInput = findViewById<TextInputEditText>(R.id.materialThicknessInput)
        val stripWidthInput = findViewById<TextInputEditText>(R.id.stripWidthInput)
        val coilCountInput = findViewById<TextInputEditText>(R.id.coilCountInput)
        val materialContainer = findViewById<LinearLayout>(R.id.materialContainer)

        val addEntryButton = findViewById<MaterialButton>(R.id.addEntryButton)
        val calculateButton = findViewById<MaterialButton>(R.id.calculateButton)
        val resetButton = findViewById<MaterialButton>(R.id.resetButton)

        messageLabel = findViewById(R.id.messageLabel)
        recommendedPalletText = findViewById(R.id.recommendedPalletText)
        entriesHeader = findViewById(R.id.entriesHeader)
        entriesContainer = findViewById(R.id.entriesContainer)
        resultsHeader = findViewById(R.id.resultsHeader)
        resultsContainer = findViewById(R.id.resultsContainer)

        val materialLabels = resources.getStringArray(R.array.material_types).drop(1)
        materialChoice = ChoiceGroup(materialContainer, materialLabels, fullWidthFirst = false, initial = -1)

        val palletSizes = resources.getStringArray(R.array.pallet_sizes)
            .mapNotNull { it.toIntOrNull() }
            .sorted()
        val palletSizeLabels = listOf(getString(R.string.pallet_size_auto)) +
            palletSizes.map { getString(R.string.pallet_size_option_format, it) }
        palletChoice = ChoiceGroup(palletSizeContainer, palletSizeLabels, fullWidthFirst = true, initial = 0)
        updateRecommendedPallet()

        // Enter/Next: prebaci fokus na sledece polje i namesti skrol iza tastature.
        val rootScroll = findViewById<ScrollView>(R.id.rootScroll)
        val inputChain = listOf(
            palletHeightInput, separatorInput, maxPalletWeightInput, targetWeightInput,
            radialThicknessInput, coreDiameterInput, materialThicknessInput,
            stripWidthInput, coilCountInput
        )
        inputChain.dropLast(1).forEachIndexed { i, input ->
            val next = inputChain[i + 1]
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    next.requestFocus()
                    rootScroll.post {
                        var y = 0
                        var v: View = next
                        while (v !== rootScroll) {
                            y += v.top
                            v = v.parent as? View ?: break
                        }
                        val margin = (96 * resources.displayMetrics.density).roundToInt()
                        rootScroll.smoothScrollTo(0, (y - margin).coerceAtLeast(0))
                    }
                    true
                } else {
                    false
                }
            }
        }
        // Kada se pritisne Done na poslednjem polju, pokreni dodavanje
        coilCountInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                addEntryButton.performClick()
                true
            } else {
                false
            }
        }

        addEntryButton.setOnClickListener {
            clearMessage()
            clearResults()
            try {
                val radial = parsePositiveDouble(
                    radialThicknessInput.text?.toString().orEmpty(),
                    "Poluprečnik trake",
                    allowZero = false
                )
                val core = parsePositiveDouble(
                    coreDiameterInput.text?.toString().orEmpty(),
                    "Unutrašnji prečnik",
                    allowZero = false
                )
                val thicknessValue = parsePositiveDouble(
                    materialThicknessInput.text?.toString().orEmpty(),
                    "Debljina materijala",
                    allowZero = false
                )
                val widthValue = parsePositiveDouble(
                    stripWidthInput.text?.toString().orEmpty(),
                    "Širina trake",
                    allowZero = false
                )
                val coilCount = parsePositiveInt(
                    coilCountInput.text?.toString().orEmpty(),
                    "Broj kotura"
                )
                val material = parseMaterial(materialLabels.getOrNull(materialChoice.selected).orEmpty())

                val dimensions = RollDimensions.fromRadialThickness(
                    radialThicknessMm = radial,
                    coreDiameterMm = core,
                    thicknessMm = thicknessValue,
                    widthMm = widthValue
                )
                val roll = computeRoll(dimensions, material)
                val entry = CoilEntry(
                    material = material,
                    dimensions = dimensions,
                    coilCount = coilCount,
                    roll = roll
                )
                entries.add(entry)
                renderEntries()
                updateRecommendedPallet()
                coilCountInput.setText("")
                showMessage(
                    getString(R.string.entry_added_format, formatValue(entry.totalWeightKg, 1)),
                    isError = false
                )
            } catch (ex: IllegalArgumentException) {
                showMessage(ex.message ?: "Greška u unosu.", isError = true)
            }
        }

        calculateButton.setOnClickListener {
            clearMessage()
            resultsContainer.removeAllViews()
            resultsHeader.visibility = View.GONE

            if (entries.isEmpty()) {
                showMessage(getString(R.string.missing_entries_error), isError = true)
                return@setOnClickListener
            }

            try {
                val palletHeight = parsePositiveDouble(
                    palletHeightInput.text?.toString().orEmpty(),
                    "Visina palete",
                    allowZero = false
                )
                val separator = parsePositiveDouble(
                    separatorInput.text?.toString().orEmpty(),
                    "Širina separatora",
                    allowZero = true
                )
                val maxPalletWeight = parseOptionalPositiveDouble(
                    maxPalletWeightInput.text?.toString().orEmpty(),
                    "Maksimalna kilaža po paleti"
                )
                val targetWeight = parseOptionalPositiveDouble(
                    targetWeightInput.text?.toString().orEmpty(),
                    "Tražena kilaža"
                )
                val manualPalletSize = if (palletChoice.selected > 0) {
                    palletSizes.getOrNull(palletChoice.selected - 1)?.toDouble()
                } else {
                    null
                }
                val palletSizeCm = manualPalletSize
                    ?: recommendedPalletSizeCm?.toDouble()
                    ?: throw IllegalArgumentException("Nema preporučene dimenzije palete.")

                val maxStackHeight = palletSizeCm * 10.0 - palletHeight
                if (maxStackHeight <= 0.0) {
                    throw IllegalArgumentException("Visina palete je veća od raspoložive visine.")
                }

                val items = buildItems(
                    entries = entries,
                    maxStackHeightMm = maxStackHeight,
                    maxPalletWeightKg = maxPalletWeight
                )
                val selection = selectItemsForTarget(items, targetWeight)
                val selectedItems = selection.items
                if (selectedItems.isEmpty()) {
                    throw IllegalArgumentException("Nema koturova za traženu kilažu.")
                }
                val totalCoils = selectedItems.size
                val totalWeight = selection.totalWeightKg
                val pallets = buildCombinedPlan(
                    items = selectedItems,
                    maxStackHeightMm = maxStackHeight,
                    palletHeightMm = palletHeight,
                    separatorMm = separator,
                    maxPalletWeightKg = maxPalletWeight
                )
                resultsHeader.text = if (targetWeight != null) {
                    getString(
                        R.string.result_header_target_format,
                        totalCoils,
                        formatValue(totalWeight, 1),
                        formatValue(targetWeight, 1)
                    )
                } else {
                    getString(
                        R.string.result_header_format,
                        totalCoils,
                        formatValue(totalWeight, 1)
                    )
                }
                resultsHeader.visibility = View.VISIBLE
                if (targetWeight != null && !selection.inTolerance) {
                    showMessage(
                        getString(
                            R.string.target_weight_warning_format,
                            formatValue(targetWeight, 1),
                            formatValue(totalWeight, 1)
                        ),
                        isError = true
                    )
                }

                pallets.forEach { pallet ->
                    val contentsText = pallet.contents.joinToString("\n") { (entryIndex, count) ->
                        getString(
                            R.string.pallet_content_format,
                            entryIndex + 1,
                            formatValue(entries[entryIndex].dimensions.radialThicknessMm, 0),
                            count
                        )
                    }
                    addResultCard(
                        resultsContainer,
                        title = getString(
                            R.string.pallet_title_size_format,
                            pallet.index,
                            palletDisplaySize(pallet, manualPalletSize?.toInt(), palletSizes, palletSizeCm.toInt())
                        ),
                        badge = getString(R.string.pallet_badge_format, pallet.coilCount),
                        subtitle = getString(
                            R.string.pallet_detail_format,
                            formatValue(pallet.totalHeightMm, 1),
                            formatValue(pallet.maxHeightMm, 1),
                            formatValue(pallet.maxHeightMm - pallet.totalHeightMm, 1),
                            formatValue(pallet.totalWeightKg, 1)
                        ) + "\n" + contentsText
                    )
                }
            } catch (ex: IllegalArgumentException) {
                showMessage(ex.message ?: "Greška u proračunu.", isError = true)
            }
        }

        resetButton.setOnClickListener {
            entries.clear()
            entriesContainer.removeAllViews()
            entriesHeader.visibility = View.GONE
            resultsContainer.removeAllViews()
            resultsHeader.visibility = View.GONE
            updateRecommendedPallet()
            showMessage(getString(R.string.reset_message), isError = false)
        }
    }

    private fun renderEntries() {
        entriesContainer.removeAllViews()
        if (entries.isEmpty()) {
            entriesHeader.visibility = View.GONE
            return
        }
        entriesHeader.text = getString(R.string.entries_header_format, entries.size)
        entriesHeader.visibility = View.VISIBLE

        entries.forEachIndexed { index, entry ->
            val dimensionsText = getString(
                R.string.entry_dimensions_format,
                entry.material,
                formatValue(entry.dimensions.radialThicknessMm, 1),
                formatValue(entry.dimensions.coreDiameterMm, 1),
                formatValue(entry.dimensions.thicknessMm, 2),
                formatValue(entry.dimensions.widthMm, 1)
            )
            val weightText = getString(
                R.string.entry_weight_format,
                formatValue(entry.roll.weightKg, 1),
                formatValue(entry.totalWeightKg, 1)
            )
            val subtitle = listOf(dimensionsText, weightText).joinToString("\n")
            addResultCard(
                entriesContainer,
                title = getString(R.string.entry_title_format, index + 1),
                badge = getString(R.string.entry_badge_format, entry.coilCount),
                subtitle = subtitle
            )
        }
    }

    private fun updateRecommendedPallet() {
        val maxOuterDiameter = entries.maxOfOrNull { it.dimensions.outerDiameterMm }
        if (maxOuterDiameter == null) {
            recommendedPalletSizeCm = null
            recommendedPalletText.text = getString(R.string.recommended_pallet_empty)
            return
        }
        val sizes = resources.getStringArray(R.array.pallet_sizes)
            .mapNotNull { it.toIntOrNull() }
            .sorted()
        if (sizes.isEmpty()) {
            recommendedPalletSizeCm = null
            recommendedPalletText.text = getString(R.string.recommended_pallet_empty)
            return
        }
        val requiredMm = maxOuterDiameter + PALLET_CLEARANCE_MM
        val recommended = sizes.firstOrNull { (it * 10.0) > requiredMm } ?: sizes.last()
        recommendedPalletSizeCm = recommended
        recommendedPalletText.text = getString(R.string.recommended_pallet_format, recommended)
    }

    private fun clearResults() {
        resultsHeader.visibility = View.GONE
        resultsContainer.removeAllViews()
    }

    private fun clearMessage() {
        messageLabel.visibility = View.GONE
    }

    private fun showMessage(message: String, isError: Boolean) {
        messageLabel.text = message
        val colorId = if (isError) R.color.error else R.color.success
        messageLabel.setTextColor(ContextCompat.getColor(this, colorId))
        messageLabel.visibility = View.VISIBLE
    }

    private fun addResultCard(
        container: LinearLayout,
        title: String,
        badge: String,
        subtitle: String
    ) {
        val card = layoutInflater.inflate(
            R.layout.item_pallet_result,
            container,
            false
        ) as MaterialCardView
        val titleView = card.findViewById<TextView>(R.id.resultTitle)
        val badgeView = card.findViewById<TextView>(R.id.resultBadge)
        val subtitleView = card.findViewById<TextView>(R.id.resultSubtitle)

        titleView.text = title
        badgeView.text = badge
        subtitleView.text = subtitle

        container.addView(card)
    }

    private fun buildItems(
        entries: List<CoilEntry>,
        maxStackHeightMm: Double,
        maxPalletWeightKg: Double?
    ): List<CoilItem> {
        val items = mutableListOf<CoilItem>()
        entries.forEachIndexed { entryIndex, entry ->
            if (entry.dimensions.widthMm > maxStackHeightMm) {
                throw IllegalArgumentException("Širina kotura je prevelika za izabranu paletu.")
            }
            if (maxPalletWeightKg != null && entry.roll.weightKg > maxPalletWeightKg) {
                throw IllegalArgumentException(
                    "Maksimalna kilaža po paleti je manja od težine jednog kotura."
                )
            }
            repeat(entry.coilCount) {
                items.add(CoilItem(entry.dimensions.widthMm, entry.roll.weightKg, entryIndex))
            }
        }
        return items
    }

    private fun selectItemsForTarget(
        items: List<CoilItem>,
        targetWeightKg: Double?
    ): TargetSelection {
        val totalWeight = items.sumOf { it.weightKg }
        if (targetWeightKg == null || items.isEmpty()) {
            return TargetSelection(items, totalWeight, true)
        }
        val minWeight = targetWeightKg * (1.0 - WEIGHT_TOLERANCE)
        val maxWeight = targetWeightKg * (1.0 + WEIGHT_TOLERANCE)
        if (totalWeight in minWeight..maxWeight) {
            return TargetSelection(items, totalWeight, true)
        }
        return pickBestItemsForTarget(items, targetWeightKg, minWeight, maxWeight)
    }

    private fun pickBestItemsForTarget(
        items: List<CoilItem>,
        targetWeightKg: Double,
        minWeightKg: Double,
        maxWeightKg: Double
    ): TargetSelection {
        val weightUnits = items.map { weightToUnits(it.weightKg) }
        val totalUnits = weightUnits.sum()
        val targetUnits = weightToUnits(targetWeightKg)
        val minUnits = ceil(minWeightKg * WEIGHT_PRECISION - 0.0001).toInt()
        val maxUnits = floor(maxWeightKg * WEIGHT_PRECISION + 0.0001).toInt()
        val prevSum = IntArray(totalUnits + 1) { -1 }
        val prevItem = IntArray(totalUnits + 1) { -1 }
        prevSum[0] = -2

        weightUnits.forEachIndexed { index, weight ->
            for (sum in totalUnits - weight downTo 0) {
                if (prevSum[sum] != -1 && prevSum[sum + weight] == -1) {
                    prevSum[sum + weight] = sum
                    prevItem[sum + weight] = index
                }
            }
        }

        val bestSum = findBestSum(
            prevSum = prevSum,
            targetUnits = targetUnits,
            minUnits = minUnits,
            maxUnits = maxUnits,
            requireNonZero = items.isNotEmpty()
        )
        if (bestSum <= 0) {
            return TargetSelection(emptyList(), 0.0, false)
        }

        val selectedFlags = BooleanArray(items.size)
        var current = bestSum
        while (current > 0) {
            val index = prevItem[current]
            if (index < 0) {
                break
            }
            selectedFlags[index] = true
            current = prevSum[current]
        }

        val selectedItems = items.filterIndexed { index, _ -> selectedFlags[index] }
        val totalWeight = selectedItems.sumOf { it.weightKg }
        val inTolerance = totalWeight in minWeightKg..maxWeightKg
        return TargetSelection(selectedItems, totalWeight, inTolerance)
    }

    private fun findBestSum(
        prevSum: IntArray,
        targetUnits: Int,
        minUnits: Int,
        maxUnits: Int,
        requireNonZero: Boolean
    ): Int {
        val start = if (requireNonZero) 1 else 0
        val end = prevSum.lastIndex
        val rangeStart = minUnits.coerceAtLeast(start)
        val rangeEnd = maxUnits.coerceAtMost(end)
        var bestSum = -1
        var bestDiff = Int.MAX_VALUE

        if (rangeStart <= rangeEnd) {
            for (sum in rangeStart..rangeEnd) {
                if (prevSum[sum] == -1) {
                    continue
                }
                val diff = abs(targetUnits - sum)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestSum = sum
                }
            }
        }
        if (bestSum >= 0) {
            return bestSum
        }
        for (sum in start..end) {
            if (prevSum[sum] == -1) {
                continue
            }
            val diff = abs(targetUnits - sum)
            if (diff < bestDiff) {
                bestDiff = diff
                bestSum = sum
            }
        }
        return bestSum
    }

    private fun weightToUnits(weightKg: Double): Int {
        return (weightKg * WEIGHT_PRECISION).roundToInt().coerceAtLeast(1)
    }

    // Bira izmedju vise planova pakovanja: najmanje paleta, pa najmanje mesanja buntova,
    // pa najravnomernija popunjenost.
    private fun buildCombinedPlan(
        items: List<CoilItem>,
        maxStackHeightMm: Double,
        palletHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?
    ): List<PalletLoad> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val candidates = listOf(
            packGlobal(items, maxStackHeightMm, separatorMm, maxPalletWeightKg),
            packByBunt(items, maxStackHeightMm, separatorMm, maxPalletWeightKg, PackMode.PURE),
            packByBunt(items, maxStackHeightMm, separatorMm, maxPalletWeightKg, PackMode.GROUPS),
            packByBunt(items, maxStackHeightMm, separatorMm, maxPalletWeightKg, PackMode.ITEMS)
        )
        val pallets = candidates.minWithOrNull(
            compareBy<List<PalletState>> { it.size }
                .thenBy { plan -> plan.sumOf { p -> p.items.map { it.entryIndex }.distinct().size - 1 } }
                .thenBy { plan -> plan.maxOf { it.coilCount } - plan.minOf { it.coilCount } }
        ) ?: candidates.first()

        return pallets.mapIndexed { index, pallet ->
            PalletLoad(
                index = index + 1,
                coilCount = pallet.coilCount,
                totalHeightMm = palletHeightMm + pallet.stackHeightMm,
                maxHeightMm = palletHeightMm + maxStackHeightMm,
                totalWeightKg = pallet.totalWeightKg,
                contents = pallet.items.groupingBy { it.entryIndex }.eachCount().toList()
                    .sortedWith(compareByDescending<Pair<Int, Int>> { entries[it.first].dimensions.outerDiameterMm }.thenBy { it.first })
            )
        }
    }

    // Rucno izabrana dimenzija vazi za sve; u auto modu svaka paleta dobija najmanju dimenziju
    // u koju staju njeni koturi (precnik + zazor) i koja drzi visinu njenog steka.
    private fun palletDisplaySize(
        pallet: PalletLoad,
        manualSizeCm: Int?,
        sizes: List<Int>,
        fallbackCm: Int
    ): Int {
        if (manualSizeCm != null) {
            return manualSizeCm
        }
        val maxOuter = pallet.contents.maxOfOrNull { entries[it.first].dimensions.outerDiameterMm }
            ?: return fallbackCm
        return sizes.firstOrNull {
            it * 10.0 > maxOuter + PALLET_CLEARANCE_MM && it * 10.0 >= pallet.totalHeightMm
        } ?: fallbackCm
    }

    private enum class PackMode { PURE, GROUPS, ITEMS }

    // Osnovni plan: svi koturi zajedno, od najsirih, uz izravnavanje broja koturova po paleti.
    private fun packGlobal(
        items: List<CoilItem>,
        maxStackHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?
    ): List<PalletState> {
        val sortedItems = items.toMutableList()
        sortedItems.sortWith(
            compareByDescending<CoilItem> { it.widthMm }.thenByDescending { it.weightKg }
        )

        val pallets = mutableListOf<PalletState>()
        sortedItems.forEach { item ->
            var bestIndex = -1
            var bestRemaining = Double.MAX_VALUE
            pallets.forEachIndexed { index, pallet ->
                if (!canAddItem(pallet, item, maxStackHeightMm, separatorMm, maxPalletWeightKg)) {
                    return@forEachIndexed
                }
                val extraHeight = if (pallet.coilCount == 0) item.widthMm else item.widthMm + separatorMm
                val remaining = maxStackHeightMm - (pallet.stackHeightMm + extraHeight)
                if (remaining < bestRemaining) {
                    bestRemaining = remaining
                    bestIndex = index
                }
            }
            if (bestIndex >= 0) {
                addItemToPallet(pallets[bestIndex], item, separatorMm)
            } else {
                pallets.add(newPallet(listOf(item), separatorMm))
            }
        }

        balancePallets(
            pallets = pallets,
            maxStackHeightMm = maxStackHeightMm,
            separatorMm = separatorMm,
            maxPalletWeightKg = maxPalletWeightKg
        )
        return pallets
    }

    // Plan po buntovima: svaki bunt ide na svoje palete (najveci precnik prvi);
    // ostaci se, zavisno od moda, spajaju cele (GROUPS) ili pojedinacno (ITEMS).
    private fun packByBunt(
        items: List<CoilItem>,
        maxStackHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?,
        mode: PackMode
    ): List<PalletState> {
        val pallets = mutableListOf<PalletState>()
        val groups = items.groupBy { it.entryIndex }.toList()
            .sortedWith(
                compareByDescending<Pair<Int, List<CoilItem>>> { entries[it.first].dimensions.outerDiameterMm }
                    .thenBy { it.first }
            )
        val remainders = mutableListOf<List<CoilItem>>()

        groups.forEach { (_, group) ->
            val sample = group.first()
            val heightCap = floor(
                (maxStackHeightMm + separatorMm) / (sample.widthMm + separatorMm) + 1e-9
            ).toInt()
            val weightCap = if (maxPalletWeightKg != null) {
                floor(maxPalletWeightKg / sample.weightKg + 1e-9).toInt()
            } else {
                Int.MAX_VALUE
            }
            val cap = minOf(heightCap, weightCap).coerceAtLeast(1)

            if (mode == PackMode.PURE) {
                // Ravnomerno rasporedi bunt na najmanji broj paleta.
                val palletCount = (group.size + cap - 1) / cap
                val base = group.size / palletCount
                val extra = group.size % palletCount
                var start = 0
                for (p in 0 until palletCount) {
                    val size = base + if (p < extra) 1 else 0
                    pallets.add(newPallet(group.subList(start, start + size), separatorMm))
                    start += size
                }
            } else {
                val fullCount = group.size / cap
                for (p in 0 until fullCount) {
                    pallets.add(newPallet(group.subList(p * cap, (p + 1) * cap), separatorMm))
                }
                val rest = group.drop(fullCount * cap)
                if (rest.isNotEmpty()) {
                    remainders.add(rest)
                }
            }
        }

        if (mode == PackMode.GROUPS) {
            remainders.sortedByDescending { r -> r.sumOf { it.widthMm } + separatorMm * r.size }
                .forEach { rest ->
                    var bestPallet: PalletState? = null
                    var bestRemaining = Double.MAX_VALUE
                    pallets.forEach { pallet ->
                        if (!canAddItems(pallet, rest, maxStackHeightMm, separatorMm, maxPalletWeightKg)) {
                            return@forEach
                        }
                        val remaining = maxStackHeightMm - pallet.stackHeightMm -
                            rest.sumOf { it.widthMm } - separatorMm * rest.size +
                            if (pallet.coilCount == 0) separatorMm else 0.0
                        if (remaining < bestRemaining) {
                            bestRemaining = remaining
                            bestPallet = pallet
                        }
                    }
                    val target = bestPallet
                    if (target != null) {
                        rest.forEach { addItemToPallet(target, it, separatorMm) }
                    } else {
                        pallets.add(newPallet(rest, separatorMm))
                    }
                }
        } else if (mode == PackMode.ITEMS) {
            remainders.flatten().forEach { item ->
                var bestPallet: PalletState? = null
                var bestSame = 2
                var bestRemaining = Double.MAX_VALUE
                pallets.forEach { pallet ->
                    if (!canAddItem(pallet, item, maxStackHeightMm, separatorMm, maxPalletWeightKg)) {
                        return@forEach
                    }
                    val same = if (pallet.items.any { it.entryIndex == item.entryIndex }) 0 else 1
                    val extraHeight = if (pallet.coilCount == 0) item.widthMm else item.widthMm + separatorMm
                    val remaining = maxStackHeightMm - (pallet.stackHeightMm + extraHeight)
                    if (same < bestSame || (same == bestSame && remaining < bestRemaining)) {
                        bestSame = same
                        bestRemaining = remaining
                        bestPallet = pallet
                    }
                }
                val target = bestPallet
                if (target != null) {
                    addItemToPallet(target, item, separatorMm)
                } else {
                    pallets.add(newPallet(listOf(item), separatorMm))
                }
            }
        }
        return pallets
    }

    private fun newPallet(items: List<CoilItem>, separatorMm: Double): PalletState {
        val pallet = PalletState(0, 0.0, 0.0, mutableListOf())
        items.forEach { addItemToPallet(pallet, it, separatorMm) }
        return pallet
    }

    private fun canAddItems(
        pallet: PalletState,
        added: List<CoilItem>,
        maxStackHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?
    ): Boolean {
        val extraHeight = added.sumOf { it.widthMm } + separatorMm * added.size -
            if (pallet.coilCount == 0) separatorMm else 0.0
        if (pallet.stackHeightMm + extraHeight > maxStackHeightMm + 1e-9) {
            return false
        }
        if (maxPalletWeightKg != null &&
            pallet.totalWeightKg + added.sumOf { it.weightKg } > maxPalletWeightKg
        ) {
            return false
        }
        return true
    }
    private fun parseMaterial(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty() || cleaned.equals(MATERIAL_PLACEHOLDER, ignoreCase = true)) {
            throw IllegalArgumentException("Materijal je obavezno polje.")
        }
        return cleaned
    }

    private fun parsePositiveInt(raw: String, fieldName: String): Int {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("$fieldName je obavezno polje.")
        }
        val value = cleaned.toIntOrNull()
            ?: throw IllegalArgumentException("$fieldName mora biti ceo broj.")
        if (value <= 0) {
            throw IllegalArgumentException("$fieldName mora biti pozitivna vrednost.")
        }
        return value
    }

    private fun parsePositiveDouble(raw: String, fieldName: String, allowZero: Boolean): Double {
        val cleaned = raw.trim().replace(",", ".")
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("$fieldName je obavezno polje.")
        }
        val value = cleaned.toDoubleOrNull()
            ?: throw IllegalArgumentException("$fieldName mora biti numerička vrednost.")
        if (allowZero && value < 0.0) {
            throw IllegalArgumentException("$fieldName mora biti nenegativna vrednost.")
        }
        if (!allowZero && value <= 0.0) {
            throw IllegalArgumentException("$fieldName mora biti pozitivna vrednost.")
        }
        return value
    }

    private fun parseOptionalPositiveDouble(raw: String, fieldName: String): Double? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) {
            return null
        }
        val value = cleaned.replace(",", ".").toDoubleOrNull()
            ?: throw IllegalArgumentException("$fieldName mora biti numerička vrednost.")
        if (value <= 0.0) {
            throw IllegalArgumentException("$fieldName mora biti pozitivna vrednost.")
        }
        return value
    }

    private fun normalizeMaterial(materialInput: String): String {
        val material = materialInput.trim().lowercase(Locale.US)
        return MATERIAL_ALIASES[material] ?: material
    }

    private fun getDensityOrThrow(materialInput: String): Double {
        val normalized = normalizeMaterial(materialInput)
        return MATERIAL_DENSITIES[normalized] ?: throw IllegalArgumentException(
            "Nepoznat materijal. Koristite Cu, CuZn10, CuZn15, CuZn20, CuZn30, CuZn37."
        )
    }

    private fun calculateRollLength(dimensions: RollDimensions): Double {
        dimensions.validate()
        val outerR = dimensions.outerRadiusMm
        val coreR = dimensions.coreRadiusMm
        val crossSectionalArea = PI * (outerR + coreR) * (outerR - coreR)
        val lengthMm = crossSectionalArea / dimensions.thicknessMm
        return lengthMm / MM_TO_M
    }

    private fun calculateRollWeight(lengthM: Double, dimensions: RollDimensions, density: Double): Double {
        val widthM = dimensions.widthMm / MM_TO_M
        val thicknessM = dimensions.thicknessMm / MM_TO_M
        val volumeM3 = lengthM * widthM * thicknessM
        return volumeM3 * density
    }

    private fun computeRoll(dimensions: RollDimensions, material: String): RollResult {
        val density = getDensityOrThrow(material)
        val length = calculateRollLength(dimensions)
        val weight = calculateRollWeight(length, dimensions, density)
        return RollResult(lengthM = length, weightKg = weight)
    }

    private fun balancePallets(
        pallets: MutableList<PalletState>,
        maxStackHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?
    ) {
        if (pallets.size < 2) {
            return
        }
        val maxIterations = pallets.sumOf { it.coilCount } * pallets.size
        var iterations = 0
        while (iterations < maxIterations) {
            var moved = false
            val donors = pallets.sortedByDescending { it.coilCount }
            val receivers = pallets.sortedBy { it.coilCount }
            for (donor in donors) {
                for (receiver in receivers) {
                    if (donor === receiver) {
                        continue
                    }
                    if (donor.coilCount - receiver.coilCount <= 1) {
                        continue
                    }
                    val itemIndex = findMovableItemIndex(
                        donor = donor,
                        receiver = receiver,
                        maxStackHeightMm = maxStackHeightMm,
                        separatorMm = separatorMm,
                        maxPalletWeightKg = maxPalletWeightKg
                    )
                    if (itemIndex >= 0) {
                        val item = removeItemFromPallet(donor, itemIndex, separatorMm)
                        addItemToPallet(receiver, item, separatorMm)
                        moved = true
                        break
                    }
                }
                if (moved) {
                    break
                }
            }
            if (!moved) {
                break
            }
            iterations += 1
        }
    }

    private fun findMovableItemIndex(
        donor: PalletState,
        receiver: PalletState,
        maxStackHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?
    ): Int {
        var bestIndex = -1
        var bestWidth = Double.MAX_VALUE
        var bestWeight = Double.MAX_VALUE
        donor.items.forEachIndexed { index, item ->
            if (!canAddItem(receiver, item, maxStackHeightMm, separatorMm, maxPalletWeightKg)) {
                return@forEachIndexed
            }
            if (item.widthMm < bestWidth || (item.widthMm == bestWidth && item.weightKg < bestWeight)) {
                bestIndex = index
                bestWidth = item.widthMm
                bestWeight = item.weightKg
            }
        }
        return bestIndex
    }

    private fun canAddItem(
        pallet: PalletState,
        item: CoilItem,
        maxStackHeightMm: Double,
        separatorMm: Double,
        maxPalletWeightKg: Double?
    ): Boolean {
        val extraHeight = if (pallet.coilCount == 0) {
            item.widthMm
        } else {
            item.widthMm + separatorMm
        }
        val newHeight = pallet.stackHeightMm + extraHeight
        if (newHeight > maxStackHeightMm) {
            return false
        }
        if (maxPalletWeightKg != null && (pallet.totalWeightKg + item.weightKg) > maxPalletWeightKg) {
            return false
        }
        return true
    }

    private fun addItemToPallet(pallet: PalletState, item: CoilItem, separatorMm: Double) {
        val extraHeight = if (pallet.coilCount == 0) {
            item.widthMm
        } else {
            item.widthMm + separatorMm
        }
        pallet.stackHeightMm += extraHeight
        pallet.totalWeightKg += item.weightKg
        pallet.coilCount += 1
        pallet.items.add(item)
    }

    private fun removeItemFromPallet(
        pallet: PalletState,
        itemIndex: Int,
        separatorMm: Double
    ): CoilItem {
        val item = pallet.items.removeAt(itemIndex)
        val extraHeight = if (pallet.coilCount <= 1) {
            item.widthMm
        } else {
            item.widthMm + separatorMm
        }
        pallet.stackHeightMm -= extraHeight
        pallet.totalWeightKg -= item.weightKg
        pallet.coilCount -= 1
        return item
    }

    private fun formatValue(value: Double, decimals: Int = 1): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
