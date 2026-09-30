# Project Map
_Generated: 2026-05-12 22:44 | Staleness: timestamps_

## Directory Structure
app/ — druga Android XML/AppCompat verzija kalkulatora pakovanja, sa proračunom kotura, težine i target weight raspodele.
app/src/main/java/com/pakovanje2/ — `MainActivity`, modeli u istom fajlu, crash logging i TextView helperi.
app/src/main/res/layout/ — XML glavni ekran i kartica rezultata.
app/src/main/res/values/ — materijali, dimenzije, tekstovi i stilovi.
.planning/ — lokalni config artefakt.

## Key Files
settings.gradle.kts — definiše Gradle root `Pakovanje2`.
app/build.gradle.kts — Android AppCompat/Material konfiguracija bez Compose-a.
app/src/main/AndroidManifest.xml — Application i launcher aktivnost.
app/src/main/java/com/pakovanje2/MainActivity.kt — centralni fajl: materijali, gustine, proračun kotura, entries, target selection i raspodela paleta.
app/src/main/java/com/pakovanje2/util/TextViewExtensions.kt — responsive TextView helperi.
app/src/main/res/layout/activity_main.xml — glavni formular za unos paleta i kotura.
app/src/main/res/layout/item_pallet_result.xml — prikaz rezultata po paleti.
app/src/main/res/values/arrays.xml — liste materijala i opcija.
pakovanje.py — Python/Kivy desktop prototip povezan po domenu.

## Critical Constraints
- Poštovati parent `AGENTS.md`: ne koristiti Git komande.
- Ovaj projekat nije isto što i `pakovanje`: ovde se računa masa kotura i ciljana težina, pa ne kopirati logiku bez poređenja.
- `MainActivity.kt` nosi i modele i poslovnu logiku; veće izmene treba raditi oprezno uz testove ili izdvajanje.
- Gustine materijala i tolerancija target weight-a su poslovna pravila, ne samo UI tekst.

## Hot Files
app/src/main/java/com/pakovanje2/MainActivity.kt, app/src/main/res/layout/activity_main.xml, app/src/main/res/layout/item_pallet_result.xml, app/src/main/res/values/arrays.xml
