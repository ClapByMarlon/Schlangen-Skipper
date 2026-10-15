# Schlangen Skipper

Eigenständiges LabyMod-3-Addon für Minecraft 1.8.9 von ClapByMarlon.
Die Botliste kann per Hotkey (standardmäßig rechte Umschalttaste) im Spiel
geöffnet werden. Suche und API-Filter wählen den Botshop aus.
Die über dieses Addon geöffneten
[GrieferUtils](https://github.com/L3g7/GrieferUtils)-Botshops erlauben nur das
Ansehen der Angebote. Normal über GrieferUtils geöffnete Botshops bleiben
unverändert.

## Voraussetzungen und Installation

- Minecraft 1.8.9 mit Forge und LabyMod 3.
- Eine kompatible [GrieferUtils-Version](https://github.com/L3g7/GrieferUtils);
  siehe auch das [GrieferUtils-Wiki](https://grieferutils.wiki/).
  Die Schnittstellen wurden gegen `2.4.8-BETA-5` geprüft.
- Die Release-JAR kommt in `LabyMod/addons-1.8` der gewünschten Instanz.
- Beim Wechsel vom Multi-Addon dessen Modul "Schlangen Skipper" deaktivieren,
  damit Hotkey und API-Abfragen nicht doppelt ausgeführt werden.
- Minecraft nach Austausch oder Installation der JAR neu starten.

## Build und Tests

Java 8 und eine lokale LabyMod-3-API werden benötigt. Die API-JAR ist
nicht Bestandteil dieses Repositorys. Vor dem ersten Build das Verzeichnis
`libs` erstellen und die passende API als `libs/lm_api_mc1.8.9.jar` ablegen.
Die Datei wird durch `.gitignore` von der Versionierung ausgeschlossen.

```powershell
.\gradlew.bat test build
```

Ergebnis: `build/libs/schlangen-skipper-1.0.5.jar`.
Die Regressionstests prüfen das explizite Öffnen, die Kaufsperre für alle
Maustasten, das erneute Sperren nach einem Seitenaufbau und die Isolation
von unabhängig geöffneten Botshops.

## Automatische Updates

Das Addon prüft beim Spielstart einmal im Hintergrund das
[neueste stabile GitHub-Release](https://github.com/ClapByMarlon/botshop-gui/releases/latest).
Eine höhere Version wird automatisch heruntergeladen. GitHub-Zugangsdaten
werden dafür nicht benötigt. Die Funktion lässt sich in den
Addon-Einstellungen unter "Automatische Updates" abschalten; die Einstellung
wird beim nächsten Start berücksichtigt.

Vor der Installation prüft der Updater Dateigröße, SHA-256-Prüfsumme,
Addon-UUID, Einstiegsklasse, Versionsnummer und Minecraft-Version. Die
laufende JAR wird nicht überschrieben. Die neue Version wird daneben
installiert, und nur die bisherige eigene JAR wird in die Löschwarteschlange
von LabyMod eingetragen. Beim nächsten Minecraft-Start entfernt LabyMod die
alte Datei vor dem Laden der Addons. Einstellungen und Caches bleiben erhalten.
Nach dem Download zeigt die Botliste einen Neustarthinweis.

Bei Netzwerkfehlern, GitHub-Rate-Limits oder ungültigen Downloads bleibt die
installierte Version erhalten. Es gibt keine automatischen Wiederholungen
während derselben Spielsitzung und keine Update-Meldungen im Chat.

### Neue Version bereitstellen

1. Versionsnummer in `gradle.properties`, `addon.json` und `mcmod.info` erhöhen.
2. `gradlew.bat test build` ausführen und den Quellstand committen.
3. Ein stabiles GitHub-Release mit dem Tag `vX.Y.Z` für diesen Commit anlegen.
4. Die fertige `schlangen-skipper-X.Y.Z.jar` als Release-Asset hochladen.
   GitHub stellt dessen SHA-256-Digest in der Release-API bereit.
5. Das Release erst nach dem Upload veröffentlichen. Entwürfe und
   Vorabversionen werden vom Updater nicht installiert.

Ein Git-Push allein liefert kein Update; erforderlich ist ein veröffentlichtes
Release mit passender JAR.
