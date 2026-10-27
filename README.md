# Schlangen Skipper

Du kannst die Botshop-GUI von überall öffnen.
Für **Minecraft 1.8.9**, von **ClapByMarlon**.

Die Botliste bietet eine Suche nach Name oder UUID und einen API-Filter.
Angebote werden über [GrieferUtils](https://github.com/L3g7/GrieferUtils)
geladen. Über Schlangen Skipper geöffnete Botshops sind nur zum Ansehen;
Käufe bleiben gesperrt. Normal über GrieferUtils geöffnete Shops bleiben
unverändert. Standard-Hotkey: **rechte Umschalttaste**.

## Version auswählen

| Ordner | Umgebung | Anleitung | Fertige lokale JAR |
| --- | --- | --- | --- |
| [Laby3](Laby3) | LabyMod 3 mit Forge | [Installation und Build](Laby3/README.md) | `Laby3/schlangen-skipper-laby3-1.0.6.jar` |
| [Laby4](Laby4) | LabyMod 4 | [Installation und Build](Laby4/README.md) | `Laby4/schlangen-skipper-laby4-1.0.2.jar` |

Beide Projekte liegen im selben Repository. LabyMod 4 übernimmt gemeinsame
Bot-, Cache-, GUI- und Updater-Quellen beim Build aus `Laby3/src`.
Die JARs sind nicht untereinander austauschbar.

## Automatische Updates

Beide Versionen prüfen beim Spielstart **einmal im Hintergrund** die
[stabilen GitHub-Releases](https://github.com/ClapByMarlon/botshop-gui/releases).
In den Addon-Einstellungen lässt sich **Automatische Updates** abschalten.
Die Einstellung wird beim nächsten Start berücksichtigt.

- LabyMod 3 lädt ausschließlich `schlangen-skipper-laby3-X.Y.Z.jar`.
- LabyMod 4 lädt ausschließlich `schlangen-skipper-laby4-X.Y.Z.jar`.
- Tag: `laby3-vX.Y.Z` beziehungsweise `laby4-vX.Y.Z`; alternativ `vX.Y.Z`.
- Der Updater durchsucht die letzten 100 Releases mit einer API-Anfrage.
  Ein neueres Release der anderen Plattform blockiert das eigene Update nicht.
- Vor der Installation werden Download-Adresse, Dateigröße, SHA-256,
  Addon-Identität, Plattform, Version und Minecraft-Version geprüft.
- Entwürfe, Vorabversionen und Downgrades werden nicht installiert.
- Bei Fehlern bleibt die installierte JAR erhalten. Keine wiederholten
  Update-Abfragen während derselben Sitzung und keine Chat-Ausgaben.

Die neue JAR wird **im tatsächlichen Addon-Verzeichnis der laufenden Instanz**
abgelegt, nicht in einem fest vorgegebenen Minecraft-Ordner. Nach dem Download
zeigt die Botliste einen Neustarthinweis. Die laufende JAR wird nie überschrieben.
Bei LabyMod 3 entfernt dessen dateibasierte Löschwarteschlange beim nächsten
Start die alte JAR. LabyMod 4 wählt beim Start die höhere Version desselben
Namespaces; die alte JAR bleibt als inaktive Rückfallkopie erhalten. Seine
namespacebasierte Löschwarteschlange wird ausdrücklich nicht verwendet.

## Updates veröffentlichen

1. Die Versionsnummer des betreffenden Projekts erhöhen, bauen und testen.
   Bei LabyMod 3 auch `src/main/resources/addon.json` und `mcmod.info` anpassen.
2. Ein stabiles GitHub-Release mit dem passenden Plattform-Tag erstellen.
3. Die fertige JAR aus dem jeweiligen Ordner als **Release-Asset** hochladen.
   GitHub liefert Dateigröße und SHA-256-Digest über seine
   [Release-API](https://docs.github.com/en/rest/releases/releases).
4. Das Release erst nach vollständigem Upload veröffentlichen.

Die lokal exportierten JARs und Build-Verzeichnisse sind in `.gitignore`
ausgeschlossen. Binärdateien müssen **nicht in Git committed** werden.
Ein Quellcode-Push allein stellt noch kein Update bereit.

### Übergang von älteren LabyMod-3-Versionen

Versionen bis 1.0.5 erwarten noch `schlangen-skipper-X.Y.Z.jar` im neuesten
Release mit Tag `vX.Y.Z`. Der LabyMod-3-Build erzeugt deshalb zusätzlich eine
identische Übergangsdatei in `Laby3/build/legacy-update/`.
Für die einmalige Migration zu 1.0.6 beide LabyMod-3-Dateien in **`v1.0.6`**
hochladen und dieses als neuestes Release markieren. LabyMod-4-Releases
während der Migration nicht als global neuestes Release markieren.
Ab 1.0.6 wählen die Clients ihren Update-Kanal unabhängig.

Lizenz: [GPL-3.0](LICENSE).
