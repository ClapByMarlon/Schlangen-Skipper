# Schlangen Skipper für LabyMod 4

Diese Variante unterstützt **LabyMod 4 auf Minecraft 1.8.9**. Sie liegt im
gleichen Repository wie die LabyMod-3-Version und wird separat gebaut.
Autor: **ClapByMarlon**.

## Funktionen

- Botshop-Browser mit der bisherigen dunklen Oberfläche und pinken Buttons.
- Chat, Scoreboard und HUD sind während der Botliste ausgeblendet. Beim
  Schließen wird die vorherige HUD-Sichtbarkeit wiederhergestellt.
- Suche nach Spielername oder UUID, API-Auswahl und bedienbare Scrollbar.
- Botlisten und Namensauflösung mit persistenten Caches und Rate-Limits.
- LabyMod-4-Einstellungen mit Aktivierung und frei wählbarer Öffnen-Taste.
- Standard-Hotkey: rechte Umschalttaste. Öffnen funktioniert nur im Spiel,
  ohne ein bereits geöffnetes Menü.
- Angebote werden über [GrieferUtils](https://github.com/L3g7/GrieferUtils)
  geladen. Käufe bleiben in der Browser-Ansicht gesperrt. Unabhängig über
  GrieferUtils geöffnete Botshops bleiben unverändert.

## Bauen

Voraussetzungen: JDK 21 und Internetzugang für das offizielle LabyMod-SDK.
Die LabyMod-3-API aus `../libs` wird nicht benötigt.

In diesem Verzeichnis ausführen:

```powershell
.\gradlew.bat build createReleaseJar
```

Installierbare Datei: `build/libs/schlangen-skipper-laby4-release.jar`.
Die kleineren Zwischen-JARs sind nicht zur Installation geeignet.

Die Tests prüfen Release-Metadaten, den GrieferUtils-ClassLoader, Übersetzungen,
den Ausschluss von LabyMod-3-/Forge-Abhängigkeiten, die gemeinsame Kaufsperre
und das Wiederherstellen der HUD-Sichtbarkeit nach dem Schließen der Botliste.

## Installation

1. LabyMod 4 für Minecraft 1.8.9 und dessen GrieferUtils-Version installieren.
2. Die Release-JAR in den `addons`-Ordner der LabyMod-4-Installation legen.
   Der Standardpfad unter Windows ist
   `%APPDATA%\.minecraft\labymod-neo\addons`.
3. Minecraft neu starten und unter „Schlangen Skipper“ den Hotkey einstellen.

Die Mindestversion von LabyMod steht als `requiredLabyModBuild` in der vom
SDK erzeugten `addon.json`. Im Spiel muss eine mindestens so neue
LabyMod-Version installiert sein.

Diese lokale Testversion verwendet keinen GitHub-Auto-Updater. Die bisher
veröffentlichten GitHub-JARs sind für LabyMod 3 und dürfen nicht als Updates
für LabyMod 4 installiert werden.

## Aufbau

`game-runner/src/v1_8_9` enthält den LabyMod-4-Einstiegspunkt und die native
Konfiguration. Gemeinsame Bot-, Cache- und GUI-Quellen werden beim Bauen aus
`../src` übernommen. Lediglich die Forge-spezifischen `IOException`-Signaturen
der GUI-Eingabemethoden werden für die Vanilla-SDK-Klassen angepasst.
Die GrieferUtils-Klassen werden in dieser Variante über die öffentliche
AddonService-API aus dem ClassLoader des installierten GrieferUtils-Addons
geladen. Die erzeugten Quellen und SDK-Dateien werden nicht versioniert.
