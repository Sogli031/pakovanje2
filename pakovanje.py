import math
from kivy.app import App
from kivy.lang import Builder
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.spinner import Spinner, SpinnerOption
from kivy.properties import StringProperty, ListProperty, NumericProperty
from kivy.core.window import Window
from kivy.utils import get_color_from_hex

# --- KONFIGURACIJA ---
MARGIN_MM = 50.0 
VALID_PALLET_CM = ["75", "83", "90", "95", "98", "105"]

# --- BOJE (Moderna Dark Tema) ---
C_BG = get_color_from_hex("#1A1B26")       # Deep Background
C_CARD = get_color_from_hex("#24283B")     # Card Background
C_ACCENT = get_color_from_hex("#7AA2F7")   # Blue Accent
C_SUCCESS = get_color_from_hex("#9ECE6A")  # Green Success
C_TEXT_MAIN = get_color_from_hex("#C0CAF5")# Light Text
C_TEXT_SUB = get_color_from_hex("#565F89") # Dimmed Text
C_INPUT_BG = get_color_from_hex("#16161E") # Darker Input BG

# --- LOGIKA PRORAČUNA ---

def max_stackable_coils(max_height: float, coil_width: float, sep: float) -> int:
    """
    Izračunava koliko koturova staje u jedan stog.
    coil_width: ovde se misli na 'širinu' kotura koja je zapravo visina slaganja (korisnik tražio preimenovanje u UI).
    """
    if coil_width <= 0: return 0
    # Formula: (MaxVisina + Separator) / (VisinaKotura + Separator)
    # Koristimo floor da dobijemo ceo broj koturova.
    return max(0, math.floor((max_height + sep) / (coil_width + sep)))

def distribute_evenly(total: int, capacity: int):
    if capacity == 0: return []
    pallets = math.ceil(total / capacity)
    while True:
        base = total // pallets
        rem = total % pallets
        sizes = [base + 1] * rem + [base] * (pallets - rem)
        if all(s <= capacity for s in sizes):
            return sizes
        pallets += 1

# --- CUSTOM WIDGETS ---

class ModernCard(BoxLayout):
    """Kontejner sa zaobljenim ivicama."""
    bg_color = ListProperty(C_CARD)
    radius = NumericProperty(15)

class ModernTextInput(TextInput):
    """Input bez bordura sa custom pozadinom."""
    pass

class ModernSpinnerOption(SpinnerOption):
    """Stil za padajući meni."""
    pass

class ResultCard(BoxLayout):
    """Kartica za prikaz rezultata jedne palete."""
    title = StringProperty("")
    subtitle = StringProperty("")
    badge_text = StringProperty("")

# --- KV DIZAJN ---

KV_CODE = f"""
#:import C_ACCENT __main__.C_ACCENT
#:import C_CARD __main__.C_CARD
#:import C_BG __main__.C_BG
#:import C_TEXT_MAIN __main__.C_TEXT_MAIN
#:import C_INPUT_BG __main__.C_INPUT_BG
#:import C_SUCCESS __main__.C_SUCCESS

# --- STILOVI WIDGETA ---

<ModernCard>:
    canvas.before:
        Color:
            rgba: self.bg_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [self.radius]

<ModernTextInput>:
    background_normal: ''
    background_active: ''
    background_color: C_INPUT_BG
    foreground_color: C_TEXT_MAIN
    cursor_color: C_ACCENT
    padding: [15, 15]
    font_size: '16sp'
    multiline: False
    write_tab: False
    hint_text_color: [0.4, 0.4, 0.5, 1]
    canvas.after:
        Color:
            rgba: C_ACCENT if self.focus else [0,0,0,0]
        Line:
            rounded_rectangle: (self.x, self.y, self.width, self.height, 6)
            width: 1.1

<ModernSpinnerOption>:
    background_normal: ''
    background_color: C_CARD
    color: C_TEXT_MAIN
    font_size: '15sp'
    height: '40dp'
    canvas.after:
        Color:
            rgba: [0.3, 0.3, 0.4, 1]
        Line:
            points: [self.x, self.y, self.x + self.width, self.y]
            width: 1

<Spinner>:
    background_normal: ''
    background_color: C_INPUT_BG
    color: C_TEXT_MAIN
    font_size: '16sp'
    option_cls: 'ModernSpinnerOption'
    canvas.before:
        Color:
            rgba: C_INPUT_BG
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [6]
    canvas.after:
        Color:
            rgba: C_ACCENT
        Triangle:
            points: [self.right - 20, self.center_y + 3, self.right - 10, self.center_y + 3, self.right - 15, self.center_y - 3]

<ResultCard>:
    orientation: 'vertical'
    size_hint_y: None
    height: '80dp'
    padding: [20, 10]
    canvas.before:
        Color:
            rgba: C_CARD
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [12]
    
    BoxLayout:
        spacing: 10
        Label:
            text: root.title
            font_size: '18sp'
            bold: True
            color: C_TEXT_MAIN
            text_size: self.size
            halign: 'left'
            valign: 'middle'
        
        Label:
            text: root.badge_text
            font_size: '20sp'
            bold: True
            color: C_ACCENT
            size_hint_x: None
            width: '100dp'
            halign: 'right'
            valign: 'middle'

    Label:
        text: root.subtitle
        font_size: '14sp'
        color: [0.6, 0.6, 0.7, 1]
        text_size: self.size
        halign: 'left'
        valign: 'top'

# --- GLAVNI LAYOUT ---

BoxLayout:
    orientation: 'vertical'
    canvas.before:
        Color:
            rgba: C_BG
        Rectangle:
            pos: self.pos
            size: self.size

    # ZAGLAVLJE
    BoxLayout:
        size_hint_y: None
        height: '70dp'
        padding: [20, 0]
        Label:
            text: "Kalkulator Pakovanja"
            font_size: '22sp'
            bold: True
            color: C_TEXT_MAIN
            text_size: self.size
            halign: 'left'
            valign: 'middle'

    # SCROLL VIEW ZA SADRŽAJ
    ScrollView:
        canvas.before:
            Color:
                rgba: [0,0,0,0]
            Rectangle:
                pos: self.pos
                size: self.size
        
        BoxLayout:
            orientation: 'vertical'
            size_hint_y: None
            height: self.minimum_height
            padding: 20
            spacing: 20

            # KARTICA ZA UNOS
            ModernCard:
                orientation: 'vertical'
                size_hint_y: None
                height: '320dp' 
                padding: 20
                spacing: 15
                radius: 15

                Label:
                    text: "Unos Podataka"
                    font_size: '14sp'
                    color: C_ACCENT
                    bold: True
                    size_hint_y: None
                    height: '30dp'
                    text_size: self.size
                    halign: 'left'

                GridLayout:
                    cols: 2
                    spacing: 15
                    
                    # Red 1: Dimenzija Palete
                    Label:
                        text: "Dimenzija Palete (cm)"
                        color: C_TEXT_MAIN
                        text_size: self.size
                        halign: 'left'
                        valign: 'middle'
                    Spinner:
                        id: pallet_spinner
                        text: '90'
                        values: app.pallet_sizes

                    # Red 2: Količina
                    Label:
                        text: "Količina (kom)"
                        color: C_TEXT_MAIN
                        text_size: self.size
                        halign: 'left'
                        valign: 'middle'
                    ModernTextInput:
                        id: input_qty
                        hint_text: 'npr. 50'
                        input_filter: 'int'

                    # Red 3: Širina Kotura (ex Visina)
                    Label:
                        text: "Širina Kotura (mm)"
                        color: C_TEXT_MAIN
                        text_size: self.size
                        halign: 'left'
                        valign: 'middle'
                    ModernTextInput:
                        id: input_width
                        hint_text: 'npr. 56.5'
                        input_filter: 'float'

                    # Red 4: Širina Separatora (NOVO)
                    Label:
                        text: "Širina Separatora (mm)"
                        color: C_TEXT_MAIN
                        text_size: self.size
                        halign: 'left'
                        valign: 'middle'
                    ModernTextInput:
                        id: input_separator
                        text: '20'
                        hint_text: 'npr. 20'
                        input_filter: 'float'

            # DUGME IZRAČUNAJ
            Button:
                text: "IZRAČUNAJ PLAN"
                size_hint_y: None
                height: '55dp'
                background_normal: ''
                background_down: ''
                background_color: [0,0,0,0]
                font_size: '16sp'
                bold: True
                on_release: app.calculate_plan()
                canvas.before:
                    Color:
                        rgba: C_SUCCESS if self.state == 'normal' else [0.5, 0.7, 0.4, 1]
                    RoundedRectangle:
                        pos: self.pos
                        size: self.size
                        radius: [10]
                    Color:
                        rgba: [1,1,1,0.2] if self.state == 'down' else [0,0,0,0]
                    RoundedRectangle:
                        pos: self.pos
                        size: self.size
                        radius: [10]

            # REZULTATI AREA
            Label:
                id: result_header
                text: ""
                markup: True
                size_hint_y: None
                height: '30dp' if self.text else 0
                opacity: 1 if self.text else 0
                color: [0.7, 0.7, 0.8, 1]

            BoxLayout:
                id: results_grid
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: 10
"""

class PackingApp(App):
    pallet_sizes = VALID_PALLET_CM
    
    def build(self):
        Window.clearcolor = get_color_from_hex("#1A1B26")
        self.root = Builder.load_string(KV_CODE)
        return self.root

    def calculate_plan(self):
        # Dohvatanje podataka iz UI
        pallet_str = self.root.ids.pallet_spinner.text
        qty_str = self.root.ids.input_qty.text
        width_str = self.root.ids.input_width.text
        sep_str = self.root.ids.input_separator.text
        
        results_area = self.root.ids.results_grid
        header_label = self.root.ids.result_header

        # Reset
        results_area.clear_widgets()
        header_label.text = ""

        # Validacija
        if not qty_str or not width_str or not sep_str:
            self.show_error("Molim unesite sve podatke.")
            return

        try:
            pallet_cm = int(pallet_str)
            pallet_mm = pallet_cm * 10
            qty = int(qty_str)
            coil_width = float(width_str)
            separator_mm = float(sep_str)
        except ValueError:
            self.show_error("Pogrešan format broja.")
            return

        if qty <= 0 or coil_width <= 0 or separator_mm < 0:
            self.show_error("Vrednosti moraju biti pozitivne.")
            return

        # Logika
        max_h_limit = pallet_mm - MARGIN_MM
        
        # Ovde prosleđujemo uneti separator
        cap_per_stack = max_stackable_coils(max_h_limit, coil_width, separator_mm)

        if cap_per_stack == 0:
            self.show_error(f"Greška: Širina kotura ({coil_width}mm) je prevelika.")
            return

        try:
            distribution = distribute_evenly(qty, cap_per_stack)
        except Exception as e:
            self.show_error(f"Greška u proračunu: {e}")
            return

        # Prikaz zaglavlja
        header_label.text = (
            f"Rezultat: {qty} kom | Max po stogu: [color=7AA2F7]{cap_per_stack}[/color]"
        )

        # Kreiranje kartica za rezultate
        row_num = 1
        for count in distribution:
            # Računanje ukupne visine sa unetim separatorom
            # Formula: (broj_komada * sirina_kotura) + ((broj_komada - 1) * separator)
            # Ako je samo 1 komad, nema separatora.
            separators_count = max(0, count - 1)
            total_h = (count * coil_width) + (separators_count * separator_mm)
            
            card = ResultCard(
                title=f"Paleta #{row_num}",
                badge_text=f"{count} kom",
                subtitle=f"Ukupna visina: {total_h:.1f} mm"
            )
            results_area.add_widget(card)
            row_num += 1

    def show_error(self, msg):
        results_area = self.root.ids.results_grid
        card = ResultCard(
            title="Greška",
            badge_text="!",
            subtitle=msg
        )
        # Promena boje kartice u crvenkastu za grešku
        card.canvas.before.children[0].rgba = [0.4, 0.2, 0.2, 1] 
        results_area.add_widget(card)

if __name__ == '__main__':
    PackingApp().run()