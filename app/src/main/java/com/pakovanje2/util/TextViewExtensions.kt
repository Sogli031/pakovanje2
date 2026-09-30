package com.pakovanje2.util

import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.core.widget.TextViewCompat

fun TextView.makeResponsive(maxLines: Int = 1) {
    this.maxLines = maxLines
    this.ellipsize = TextUtils.TruncateAt.END
}

fun TextView.enableAutoSize(
    minSizeSp: Int = 10,
    maxSizeSp: Int = 18,
    stepSp: Int = 1
) {
    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
        this,
        minSizeSp,
        maxSizeSp,
        stepSp,
        TypedValue.COMPLEX_UNIT_SP
    )
    this.maxLines = 1
    this.ellipsize = TextUtils.TruncateAt.END
}

fun TextView.setTextSafe(text: CharSequence?) {
    this.text = text ?: ""
}
