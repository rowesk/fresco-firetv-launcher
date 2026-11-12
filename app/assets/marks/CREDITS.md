# Dock marks

Each app in the dock is shown as a mark: white at rest, in its brand colour on focus. The app's own icon is never shown. `MarkArt.java` maps package names to these files and sets each one's colour. To see every mark at once, run `app/tools/preview-marks.sh`.

Brand names and marks belong to their owners. Fresco isn't affiliated with or endorsed by any of them. The marks are used only to show which app a dock button opens.

## From Simple Icons

These SVG files are unmodified copies from [Simple Icons](https://simpleicons.org) **16.37.0** (CC0 1.0), fetched from `https://cdn.jsdelivr.net/npm/simple-icons@16.37.0/icons/<slug>.svg`. CC0 covers the drawings, not the trademarks; see Simple Icons' [disclaimer](https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md).

| File | Brand | Simple Icons slug |
|---|---|---|
| `netflix.svg` | Netflix | `netflix` (the N) |
| `itvx.svg` | ITVX | `itvx` |
| `channel4.svg` | Channel 4 | `channel4` |
| `spotify.svg` | Spotify | `spotify` |
| `hbomax.svg` | HBO Max | `hbomax` |
| `appletv.svg` | Apple TV | `appletv` |
| `paramountplus.svg` | Paramount+ | `paramountplus` |
| `now.svg` | NOW | `now` |
| `dazn.svg` | DAZN | `dazn` |
| `crunchyroll.svg` | Crunchyroll | `crunchyroll` |
| `youtubekids.svg` | YouTube Kids | `youtubekids` |
| `youtubemusic.svg` | YouTube Music | `youtubemusic` |
| `twitch.svg` | Twitch | `twitch` |
| `plex.svg` | Plex | `plex` |
| `jellyfin.svg` | Jellyfin | `jellyfin`. Jellyfin's own artwork is CC BY-SA 4.0 ([jellyfin-ux](https://github.com/jellyfin/jellyfin-ux)). |
| `kodi.svg` | Kodi | `kodi` |
| `emby.svg` | Emby | `emby` |
| `vlcmediaplayer.svg` | VLC | `vlcmediaplayer` |
| `nvidia.svg` | GeForce NOW | `nvidia` (Simple Icons has no GeForce mark) |
| `steam.svg` | Steam Link | `steam` |
| `tubi.svg` | Tubi | `tubi` |
| `rakuten.svg` | Rakuten TV | `rakuten` |
| `viaplay.svg` | Viaplay | `viaplay` |
| `zdf.svg` | ZDF Mediathek | `zdf` |
| `fubo.svg` | Fubo | `fubo` |
| `deezer.svg` | Deezer | `deezer` |
| `tidal.svg` | TIDAL | `tidal` |

YouTube TV uses Fresco's hand-drawn YouTube mark (in `Marks.java`). Simple Icons' `youtubetv` is a wordmark, and it can't be read at dock size.

## Original designs

Simple Icons doesn't carry these brands, so Fresco gives each one an original glyph instead: a simple idea in the brand's colour, not a copy or trace of its logo. They're drawn in `app/tools/DrawMarks.java` (`tools/preview-marks.sh --draw` regenerates them) and are released under Fresco's MIT licence.

| File | App | The idea |
|---|---|---|
| `primevideo.svg` | Prime Video | A blue disc with a play triangle cut out |
| `disneyplus.svg` | Disney+ | A plus under an orbiting arc, in deep blue |
| `iplayer.svg` | BBC iPlayer | A soft play triangle, pink to magenta |
| `my5.svg` | My5 / 5 | A geometric 5 |
| `amazonmusic.svg` | Amazon Music | Two beamed notes in cyan |
| `u.svg` | U (UKTV Play) | A round-ended U in coral |
| `plutotv.svg` | Pluto TV | A yellow planet with an ink ring |
| `peacock.svg` | Peacock | A fan of six coloured dots around a pivot |
| `hulu.svg` | Hulu | A rounded lowercase h in green |
| `xboxcloud.svg` | Xbox Cloud Gaming | A green cloud with a d-pad and buttons cut out |
| `moonlight.svg` | Moonlight | A crescent |

Every other app gets a monogram: the first letter or digit of its name in Red Hat Display (OFL), cut from a rounded tile, coloured on focus from the app's icon.
