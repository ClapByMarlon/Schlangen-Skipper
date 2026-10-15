# Schlangen Skipper

Eigenstaendiges LabyMod-3-Addon fuer Minecraft 1.8.9 von ClapByMarlon.
Die Botliste kann per Hotkey (standardmaessig rechte Umschalttaste) im Spiel
geoeffnet werden. Suche und API-Filter waehlen den Botshop aus.
Die ueber dieses Addon geoeffneten
[GrieferUtils](https://github.com/L3g7/GrieferUtils)-Botshops erlauben nur das
Ansehen der Angebote. Normal ueber GrieferUtils geoeffnete Botshops bleiben
unveraendert.

## Voraussetzungen und Installation

- Minecraft 1.8.9 mit Forge und LabyMod 3.
- Eine kompatible [GrieferUtils-Version](https://github.com/L3g7/GrieferUtils);
  siehe auch das [GrieferUtils-Wiki](https://grieferutils.wiki/).
  Die Schnittstellen wurden gegen `2.4.8-BETA-5` geprueft.
- Die Release-JAR kommt in `LabyMod/addons-1.8` der gewuenschten Instanz.
- Beim Wechsel vom Multi-Addon dessen Modul "Schlangen Skipper" deaktivieren,
  damit Hotkey und API-Abfragen nicht doppelt ausgefuehrt werden.
- Minecraft nach Austausch oder Installation der JAR neu starten.

## Build und Tests

Java 8 und eine lokale LabyMod-3-API werden benoetigt. Die API-JAR ist
nicht Bestandteil dieses Repositorys. Vor dem ersten Build das Verzeichnis
`libs` erstellen und die passende API als `libs/lm_api_mc1.8.9.jar` ablegen.
Die Datei wird durch `.gitignore` von der Versionierung ausgeschlossen.

```powershell
.\gradlew.bat test build
```

Ergebnis: `build/libs/schlangen-skipper-1.0.3.jar`.
Die Regressionstests pruefen das explizite Oeffnen, die Kaufsperre fuer alle
Maustasten, das erneute Sperren nach einem Seitenaufbau und die Isolation
von unabhaengig geoeffneten Botshops.

## Automatische Updates

Ab Version 1.0.2 prueft das Addon beim Spielstart einmal im Hintergrund das
[neueste stabile GitHub-Release](https://github.com/ClapByMarlon/botshop-gui/releases/latest).
Eine hoehere Version wird automatisch heruntergeladen. GitHub-Zugangsdaten
werden dafuer nicht benoetigt. Die Funktion laesst sich in den
Addon-Einstellungen unter "Automatische Updates" abschalten; die Einstellung
wird beim naechsten Start beruecksichtigt.

Vor der Installation prueft der Updater Dateigroesse, SHA-256-Pruefsumme,
Addon-UUID, Einstiegsklasse, Versionsnummer und Minecraft-Version. Die
laufende JAR wird nicht ueberschrieben. Die neue Version wird daneben
installiert, und nur die bisherige eigene JAR wird in die Loeschwarteschlange
von LabyMod eingetragen. Beim naechsten Minecraft-Start entfernt LabyMod die
alte Datei vor dem Laden der Addons. Einstellungen und Caches bleiben erhalten.
Nach dem Download zeigt die Botliste einen Neustarthinweis.

Bei Netzwerkfehlern, GitHub-Rate-Limits oder ungueltigen Downloads bleibt die
installierte Version erhalten. Es gibt keine automatischen Wiederholungen
waehrend derselben Spielsitzung und keine Update-Meldungen im Chat.

### Neue Version bereitstellen

1. Versionsnummer in `gradle.properties`, `addon.json` und `mcmod.info` erhoehen.
2. `gradlew.bat test build` ausfuehren und den Quellstand committen.
3. Ein stabiles GitHub-Release mit dem Tag `vX.Y.Z` fuer diesen Commit anlegen.
4. Die fertige `schlangen-skipper-X.Y.Z.jar` als Release-Asset hochladen.
   GitHub stellt dessen SHA-256-Digest in der Release-API bereit.
5. Das Release erst nach dem Upload veroeffentlichen. Entwuerfe und
   Vorabversionen werden vom Updater nicht installiert.

Ein Git-Push allein liefert kein Update; erforderlich ist ein veroeffentlichtes
Release mit passender JAR. Die erste Installation einer Version mit Updater
erfolgt manuell, aeltere Versionen ohne Updater aktualisieren sich nicht selbst.

## Aenderung in 1.0.1

Neuere GrieferUtils-Versionen oeffnen `BotshopGUI` erst durch `open()`.
Der Konstruktor allein reicht nicht mehr aus. Der Aufruf erfolgt jetzt
explizit, und die Kaufsperre wird vor und nach dem Oeffnen sowie vor der
Mauseingabe gesetzt. Bei inkompatiblen Kauf-Callbacks wird die Ansicht
nicht freigegeben. Ladefehler erscheinen in der Botlisten-Statuszeile.

Die Botlisten- und Botshop-Request-Sperrzeiten bleiben erhalten.
Der authentifizierte Itemabruf benoetigt eine gueltige Minecraft-Sitzung;
er wird weiterhin durch GrieferUtils ausgefuehrt.
