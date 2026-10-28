# Schlangen Skipper für LabyMod 3

Minecraft **1.8.9 mit Forge und LabyMod 3**, Autor: **ClapByMarlon**.
Benötigt eine kompatible [GrieferUtils-Version](https://github.com/L3g7/GrieferUtils).
Die Shop-Schnittstellen wurden gegen `2.4.8-BETA-5` geprüft.

## Installation

`schlangen-skipper-laby3-1.0.8.jar` in `LabyMod/addons-1.8` der gewünschten
Instanz legen und Minecraft neu starten. Nicht zusätzlich eine zweite
Schlangen-Skipper-JAR oder das gleichnamige Modul eines Multi-Addons aktivieren.

Die Botliste öffnet sich im Spiel mit der rechten Umschalttaste.
Suche und API-Filter wählen einen Botshop aus. Die über dieses Addon
geöffneten Shops erlauben nur das Ansehen der Angebote.

## Favoriten

Der Stern rechts in jeder Bot-Zeile fügt den Bot zu den Favoriten hinzu.
Ein goldener Stern kennzeichnet gespeicherte Bots; ein erneuter Klick entfernt
sie wieder. Der Stern öffnet keinen Shop.

Der Button **Favoriten** schaltet zur Favoritenansicht, **Alle Bots** zurück
zur normalen Liste. Suche und API-Filter funktionieren auch in den Favoriten.
Beim Wechsel zu den Favoriten werden beide Filter zurückgesetzt, damit
zunächst alle gespeicherten Bots angezeigt werden.
Die zuletzt gewählte Ansicht wird beim erneuten Öffnen und nach einem
Spielneustart wiederhergestellt. Neue Installationen beginnen mit allen Bots.

Favoriten werden anhand der UUID und ihrer Shop-API gespeichert unter
`LabyMod/addons-1.8/config/schlangen-skipper-favorites.json` der jeweiligen
Instanz. Namensänderungen und vorübergehend fehlende API-Listen löschen sie
nicht. Bei Lese- oder Schreibfehlern erscheint ein Hinweis im Browser;
unlesbare Dateien werden nicht mit einer leeren Liste überschrieben.

Diese Funktion ist zunächst ausschließlich für LabyMod 3 aktiviert.

Das Addon-Icon liegt unter `src/main/resources/assets/schlangen_skipper/textures/icon.png`.
LabyMod 3 lädt es über die GitHub-URL aus `addon.json`; dafür ist eine
Internetverbindung erforderlich. Die PNG wird zusätzlich in die JAR gepackt.

## Bauen

Java 8 und eine lokale LabyMod-3-API werden benötigt. Die API-JAR ist nicht
Bestandteil des Repositorys: `libs/lm_api_mc1.8.9.jar` in diesem Ordner ablegen.

Im Ordner `Laby3` ausführen:

```powershell
.\gradlew.bat test build
```

Installierbare Datei: **`schlangen-skipper-laby3-1.0.8.jar`**, direkt in `Laby3`.
Sie entspricht der reobfuskierten Datei in `build/libs/`.
`build/legacy-update/schlangen-skipper-1.0.8.jar` enthält identische Bytes,
ist aber nur für die Migration vom bisherigen Updater gedacht.

## Updates

**Automatische Updates** lässt sich in den Addon-Einstellungen abschalten.
Einmal pro Spielstart werden stabile GitHub-Releases geprüft, ausschließlich
für LabyMod 3. Eine validierte neue JAR wird neben der laufenden Datei abgelegt;
LabyMods `.delete`-Warteschlange entfernt beim nächsten Start nur die alte Datei.
Einstellungen, Caches und andere Addons bleiben erhalten.

Tags: `laby3-vX.Y.Z` oder `vX.Y.Z`.
Asset: `schlangen-skipper-laby3-X.Y.Z.jar`.
Weitere Sicherheitsprüfungen und Veröffentlichungsschritte stehen in der
[gemeinsamen Anleitung](../README.md#automatische-updates).

Lizenz: [GPL-3.0](../LICENSE).
