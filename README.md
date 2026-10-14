# Needle Voice — assistente vocale Android (prototipo)

Assistente vocale in italiano, con attivazione premendo il microfono e arresto automatico dopo alcuni secondi senza voce.

## Funzioni presenti nel progetto
- Riconoscimento vocale Android in italiano.
- Note rapide salvate localmente.
- Timer interno con annuncio vocale.
- Apertura di Tapo, Smart Life, WhatsApp, orologio, ricerca meteo, YouTube, Maps, fotocamera e impostazioni.
- Composizione di messaggi WhatsApp, SMS e chiamate nel dialer; l'utente conferma/invia nelle app di destinazione.
- Apertura del modulo per creare eventi nel calendario.
- Navigazione tramite Google Maps.
- **Controllo diretto smart home tramite Home Assistant REST API**: accensione/spegnimento di luci e prese esposte a Home Assistant, anche con comandi vocali multipli per tutte le luci/prese.

## Configurare il controllo smart home
1. Installa Home Assistant e aggiungi i tuoi dispositivi Tapo/Tuya/Smart Life tramite integrazioni compatibili.
2. In Home Assistant, dal profilo utente, crea un token di accesso a lunga durata.
3. Nell'app premi **Configura Home Assistant**, inserisci l'URL raggiungibile dal telefono (es. `http://192.168.1.20:8123`) e il token.
4. Premi **Verifica connessione smart home**.
5. Prova: “accendi la luce cucina”, “spegni la presa del salotto”, “spegni tutte le luci”. I nomi devono corrispondere ai nomi delle entità in Home Assistant.

**Sicurezza:** il token viene salvato nelle preferenze private dell'app, ma non è cifrato con una chiave dedicata. Per l'accesso remoto preferisci HTTPS e non esporre Home Assistant direttamente a Internet. Il traffico HTTP è abilitato per supportare gli indirizzi locali tipici di Home Assistant; usalo solo su una rete fidata.

## Limiti importanti
- Questo progetto **non integra ancora il runtime nativo Cactus/Needle**: l'ascolto usa il riconoscimento vocale Android e può dipendere dal provider vocale configurato nel telefono. L'integrazione Needle richiede il runtime Android nativo e i pesi compatibili del modello.
- Il controllo diretto di Tapo/Smart Life non usa le API private delle loro app: i dispositivi devono essere collegati a Home Assistant e visibili come entità `light.*` o `switch.*`.
- WhatsApp, SMS e chiamate vengono preparati/aperti per conferma da parte dell'utente; l'app non invia messaggi o chiama in segreto.
- Il timer interno si interrompe se Android termina il processo dell'app; per timer persistenti è preferibile passare l'azione al sistema Orologio.
- Il riconoscimento vocale è attivato esplicitamente dal pulsante, non rimane in ascolto sempre.

## Compilazione APK
Il progetto usa Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35 e JDK 17+.

Apri la cartella `NeedleVoice` in Android Studio, attendi il Gradle Sync e seleziona **Build > Build APK(s)**. L'APK debug, se la build riesce, sarà in `app/build/outputs/apk/debug/app-debug.apk`.

L'ambiente in cui è stato generato questo archivio non dispone di Android SDK/Gradle e non riesce a risolvere host esterni per scaricare le dipendenze, quindi non è stato possibile compilare o testare un APK in questa sessione.

## Compilazione cloud senza installare Android Studio (GitHub Actions)

Il progetto include `.github/workflows/build-apk.yml`, che compila automaticamente l'APK su un runner GitHub con Android SDK e JDK.

1. Crea un repository GitHub vuoto.
2. Carica il contenuto della cartella `NeedleVoice` nel repository (non la cartella ZIP intera come singolo file).
3. Apri la scheda **Actions** e avvia **Build Android APK** con **Run workflow**; in alternativa parte al push su `main` o `master`.
4. Quando il workflow termina con successo, apri la sua esecuzione, scorri fino ad **Artifacts** e scarica `NeedleVoice-debug-apk`.
5. Estrai lo ZIP dell'artifact e installa `app-debug.apk` sul telefono Android. Se richiesto, autorizza l'installazione da quella sorgente.

Questo workflow è una via di compilazione cloud, non una prova che l'APK sia già stato compilato o testato. L'APK debug non è firmato per la distribuzione Play Store. La compilazione potrebbe segnalare errori del progetto da correggere; il workflow mostrerà i log completi.
