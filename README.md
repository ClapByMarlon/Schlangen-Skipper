# Schlangen Skipper

Eigenstaendiges LabyMod-3-Addon fuer Minecraft 1.8.9 von ClapByMarlon.
Die Botliste kann per Hotkey (standardmaessig rechte Umschalttaste) im Spiel
geoeffnet werden. Suche und API-Filter waehlen den Botshop aus.
Die ueber dieses Addon geoeffneten GrieferUtils-Botshops erlauben nur das
Ansehen der Angebote. Normal ueber GrieferUtils geoeffnete Botshops bleiben
unveraendert.

## Voraussetzungen und Installation

- Minecraft 1.8.9 mit Forge und LabyMod 3.
- Eine kompatible GrieferUtils-Version; die Schnittstellen wurden gegen
  `2.4.8-BETA-5` geprueft.
- Die Release-JAR kommt in `LabyMod/addons-1.8` der gewuenschten Instanz.
- Beim Wechsel vom Multi-Addon dessen Modul "Schlangen Skipper" deaktivieren,
  damit Hotkey und API-Abfragen nicht doppelt ausgefuehrt werden.
- Minecraft nach Austausch oder Installation der JAR neu starten.

## Build und Tests

Java 8 und die vorhandene lokale LabyMod-API in `libs/lm_api_mc1.8.9.jar`
werden benoetigt.

```powershell
.\gradlew.bat test build
```

Ergebnis: `build/libs/schlangen-skipper-1.0.1.jar`.
Die Regressionstests pruefen das explizite Oeffnen, die Kaufsperre fuer alle
Maustasten, das erneute Sperren nach einem Seitenaufbau und die Isolation
von unabhaengig geoeffneten Botshops.

## Aenderung in 1.0.1

Neuere GrieferUtils-Versionen oeffnen `BotshopGUI` erst durch `open()`.
Der Konstruktor allein reicht nicht mehr aus. Der Aufruf erfolgt jetzt
explizit, und die Kaufsperre wird vor und nach dem Oeffnen sowie vor der
Mauseingabe gesetzt. Bei inkompatiblen Kauf-Callbacks wird die Ansicht
nicht freigegeben. Ladefehler erscheinen in der Botlisten-Statuszeile.

Die Botlisten- und Botshop-Request-Sperrzeiten bleiben erhalten.
Der authentifizierte Itemabruf benoetigt eine gueltige Minecraft-Sitzung;
er wird weiterhin durch GrieferUtils ausgefuehrt.
