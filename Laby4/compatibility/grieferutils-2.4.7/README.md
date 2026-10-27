# GrieferUtils-2.4.7-Kompatibilitätsfix

Inoffizielle lokale Testversion für LabyMod 48, Build 3231 / Minecraft 1.8.9.
GrieferUtils stammt von [L3g7 und L3g73](https://github.com/L3g7/GrieferUtils)
und steht unter Apache-2.0, nicht unter der Lizenz von Schlangen Skipper.

## Änderung

Das Mixin-Plugin überspringt optionale Schematica-/Lunatrius- und Forge-Ziele,
wenn deren Klassen fehlen. Das geschieht vor dem Zählen der Mixins, damit der
GrieferUtils-Listener weiterhin korrekt funktioniert. Vorhandene Integrationen
und erforderliche Minecraft-/LabyMod-Ziele werden nicht deaktiviert.
Die Fehlerunterdrückung für die alte Kosmetik-Klasse `GeometryColor` wird
ebenfalls übersprungen, falls diese Klasse in LabyMod 4 nicht vorhanden ist.
Dasselbe gilt für die Hauptmenü-Kopf-Hooks auf `Textures$Splash` und
`Schematic`, die in aktuellen LabyMod-4-Builds nicht mehr existieren.
Fehlende Ziele aus alten LabyMod-3-Namespaces werden nicht in LabyMod 4
registriert. Aktuelle API-/Core-Ziele von LabyMod 4 bleiben erforderlich.

Der Event-Adapter implementiert die aktuelle `SubscribeMethod`-Schnittstelle
einschließlich Event-Flags und Replay-Markierung. Callback und Priorität
bleiben erhalten. Die optionale Prozent-Anzeige des Tablisten-Badges wird
bei nicht mehr passender Local-Variable-Tabelle ausgelassen, statt den
gesamten Client zu unterbrechen.

Das vorhandene Debug-Build-Flag wird auf `true` gesetzt. Dadurch prüft der
offizielle Updater die Versionsnummer statt der veränderten Prüfsumme und
ersetzt den Fix nicht sofort durch dieselbe Originalversion. Updates auf
neuere Versionen bleiben möglich.

## Bauen

JDK 21 verwenden und den LabyMod-4-Build im übergeordneten Projekt einmal
ausführen, damit dessen festgelegte SDK-API im Gradle-Cache vorhanden ist.
Die unveränderte
[offizielle v2.4.7-JAR](https://github.com/L3g7/GrieferUtils/releases/tag/v2.4.7)
muss unter `../../game-runner/libs/griefer-utils-v2.4.7.jar` liegen.
Der Build prüft ihre SHA-256-Prüfsumme vor der Kompilierung.

Aus diesem Verzeichnis:

```powershell
..\..\gradlew.bat build
```

Ergebnis: `build/libs/griefer-utils-v2.4.7-optional-fix.jar`.
Die Original-JAR wird nicht überschrieben.

## Installation

Minecraft vollständig schließen. Nur die bisherige GrieferUtils-JAR durch
diese Testversion ersetzen; die Originaldatei außerhalb des Addon-Ordners
aufbewahren. Nicht beide GrieferUtils-JARs gleichzeitig installieren.
Schlangen Skipper 1.0.1 bleibt zusätzlich installiert.

Diese Datei ist keine offizielle GrieferUtils-Veröffentlichung. Ein späteres
GrieferUtils-Update kann den lokalen Fix ersetzen. Der Fix behandelt fehlende
optionale Mixin-Ziele, nicht beliebige weitere Inkompatibilitäten mit LabyMod.
