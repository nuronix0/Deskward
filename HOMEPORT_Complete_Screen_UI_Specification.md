# HOMEPORT --- Complete Screen, Page & UI Component Specification

**Project:** HOMEPORT\
**Platforms:** Android + Laptop/Desktop\
**Purpose:** Complete UI master checklist for the bidirectional HOMEPORT
application.

> **Important:** All items in this document are the functional UI
> checklist. They are not a rigid requirement to create one separate
> screen for every item. Screens may be merged, split, removed, replaced
> with dialogs/bottom sheets/tabs, or newly added whenever the actual
> workflow requires it. The complete app structure must be designed
> after understanding the whole HOMEPORT usage flow.

## 1. Product UX model

HOMEPORT is a peer-to-peer private device network. The phone and laptop
are peers; either can browse, search, preview, upload, download, and
manage the other device's explicitly authorized storage.

Core flow:
`Pair → Trust → Authorize → Connect → Browse/Search → Preview → Transfer → Manage → Monitor → Recover → Revoke`

The UI must hide networking complexity such as WebRTC, STUN/TURN,
signaling, indexing, chunking, and integrity verification unless the
user opens diagnostics.

## 2. Android navigation

Recommended primary destinations: - Home - Devices - Transfers -
Activity - Settings

Contextual destinations include Remote Explorer, Search, Preview, File
Details, Storage, and Jobs.

### 2.1 Onboarding screens

-   Splash / initialization --- MUST HAVE
-   Welcome --- MUST HAVE
-   How HOMEPORT works --- SHOULD HAVE
-   Create device identity --- MUST HAVE
-   Storage-access explanation --- MUST HAVE
-   Select shared folders --- MUST HAVE

### 2.2 Home screens

-   Home dashboard --- MUST HAVE
-   No-devices empty state --- MUST HAVE
-   Device offline state --- MUST HAVE
-   Quick-actions sheet --- OPTIONAL

Home should show connected devices, connection state, storage summary,
recent files, recent transfers, and important actions.

### 2.3 Device and pairing screens

-   Devices list --- MUST HAVE
-   Pair new device --- MUST HAVE
-   QR scanner --- MUST HAVE
-   Show my pairing QR --- MUST HAVE
-   Pairing confirmation --- MUST HAVE
-   Pairing success --- MUST HAVE
-   Pairing failure --- MUST HAVE
-   Trusted device detail --- MUST HAVE
-   Connection diagnostics --- SHOULD HAVE
-   Revoke-device confirmation --- MUST HAVE

Device detail should show name, type, online state, last seen, storage,
permissions, shared folders, connection route, and actions such as
Browse, Search, Permissions, Rename, and Revoke.

### 2.4 Remote file screens

-   Remote device overview --- MUST HAVE
-   Remote device offline page/state --- MUST HAVE
-   Root explorer --- MUST HAVE
-   Folder explorer --- MUST HAVE
-   List view --- MUST HAVE
-   Grid view --- SHOULD HAVE
-   Folder empty state --- MUST HAVE
-   Folder loading/skeleton state --- MUST HAVE
-   Large-directory progressive loading state --- MUST HAVE
-   Multi-select mode --- MUST HAVE
-   Sort/view sheet --- MUST HAVE
-   Filter sheet --- MUST HAVE

File actions: - File context menu --- MUST HAVE - Folder context menu
--- MUST HAVE - Rename dialog --- MUST HAVE - Create-folder dialog ---
MUST HAVE - Move destination page/sheet --- MUST HAVE - Copy destination
page/sheet --- MUST HAVE - Delete confirmation --- MUST HAVE - File
details --- MUST HAVE

### 2.5 Search screens

-   Remote search screen --- MUST HAVE
-   Search suggestions --- SHOULD HAVE
-   Search filters --- MUST HAVE
-   Search results --- MUST HAVE
-   Search empty state --- MUST HAVE
-   Search error state --- MUST HAVE
-   Full-text result view --- SHOULD HAVE

Search results should show file icon/thumbnail, name, path, size,
modified time, device, and relevant snippets where full-text search is
enabled. Results should load progressively.

### 2.6 Preview screens

-   Universal preview shell --- MUST HAVE
-   Image preview --- MUST HAVE
-   PDF preview --- MUST HAVE
-   Video preview --- SHOULD HAVE
-   Audio preview --- SHOULD HAVE
-   Text preview --- MUST HAVE
-   Code preview --- SHOULD HAVE
-   Archive information preview --- SHOULD HAVE
-   Unsupported-file preview --- MUST HAVE
-   Preview loading --- MUST HAVE
-   Preview error --- MUST HAVE

Supported text/code examples: TXT, LOG, MD, JSON, XML, CSV, Kotlin,
Java, JavaScript, TypeScript, Python, C, C++, HTML, CSS, SQL, YAML.

### 2.7 Transfer screens

-   Transfers dashboard --- MUST HAVE
-   Active transfer detail --- MUST HAVE
-   Transfer queue --- MUST HAVE
-   Completed transfer detail --- SHOULD HAVE
-   Failed transfer detail --- MUST HAVE
-   Resume-transfer confirmation/state --- MUST HAVE
-   Transfer conflict dialog --- MUST HAVE
-   Integrity-verification state --- MUST HAVE
-   Paused-transfer state --- MUST HAVE
-   Destination-storage warning --- MUST HAVE

Every transfer should expose direction, filename, progress, bytes,
speed, ETA when reliable, state, and available actions. Advanced detail
may expose retries/chunks/connection route.

### 2.8 Upload/send screens

-   Send/select-files screen --- MUST HAVE
-   Destination-device selection --- MUST HAVE
-   Remote destination-folder selection --- MUST HAVE
-   Upload permission state --- MUST HAVE
-   Upload confirmation --- SHOULD HAVE

### 2.9 Storage screens

-   Storage dashboard --- MUST HAVE
-   Storage categories --- SHOULD HAVE
-   Largest files --- SHOULD HAVE
-   Storage analytics --- SHOULD HAVE
-   Duplicate analysis --- OPTIONAL / ADVANCED

### 2.10 Remote jobs

-   Jobs dashboard --- SHOULD HAVE
-   Create-job screen --- SHOULD HAVE
-   Job configuration --- SHOULD HAVE
-   Job progress --- SHOULD HAVE
-   Job result --- SHOULD HAVE

MVP jobs can include ZIP a folder, generate thumbnails/previews, and
calculate a file hash.

### 2.11 Activity

-   Activity dashboard --- MUST HAVE
-   Activity detail --- SHOULD HAVE
-   Activity filters --- SHOULD HAVE

Events include pairing, connections, transfers, file operations,
permission changes, jobs, failures, and revocations.

### 2.12 Settings

-   Settings main --- MUST HAVE
-   Device identity --- MUST HAVE
-   Shared folders --- MUST HAVE
-   Folder permission editor --- MUST HAVE
-   Trusted devices --- MUST HAVE
-   Transfer settings --- SHOULD HAVE
-   Search/index settings --- SHOULD HAVE
-   Notification settings --- SHOULD HAVE
-   Security settings --- MUST HAVE
-   Appearance --- SHOULD HAVE
-   Advanced --- OPTIONAL
-   About/help --- MUST HAVE

Permissions should be expressed as READ, WRITE, UPLOAD, DOWNLOAD, MOVE,
COPY, DELETE, and PROCESS, with human-readable explanations.

## 3. Laptop/Desktop navigation

Recommended shell:
`Sidebar → Main content → Optional preview/details pane → Persistent transfer tray`

Primary navigation: - Home - Devices - Files - Search - Transfers -
Jobs - Storage - Activity - Settings

### 3.1 Desktop onboarding

-   Startup/initialization --- MUST HAVE
-   First-launch explanation --- MUST HAVE
-   Device setup --- MUST HAVE
-   Shared-folder setup --- MUST HAVE

### 3.2 Desktop shell

-   Main dashboard --- MUST HAVE
-   Sidebar --- MUST HAVE
-   Header --- MUST HAVE
-   Global connection indicator --- MUST HAVE
-   Optional transfer tray --- SHOULD HAVE

### 3.3 Desktop devices

-   Devices page --- MUST HAVE
-   Device detail --- MUST HAVE
-   Pair device --- MUST HAVE
-   Trusted device detail --- MUST HAVE
-   Permissions editor --- MUST HAVE
-   Revoke confirmation --- MUST HAVE

### 3.4 Desktop files

-   Files home --- MUST HAVE
-   Local file explorer --- MUST HAVE
-   Remote file explorer --- MUST HAVE
-   Folder explorer --- MUST HAVE
-   File table --- MUST HAVE
-   Grid view --- SHOULD HAVE
-   Multi-select --- MUST HAVE
-   Context menu --- MUST HAVE
-   File details --- MUST HAVE
-   Rename/create/move/copy/delete interactions --- MUST HAVE

Desktop should support drag-and-drop, keyboard selection,
right-click/context menus, sorting, filtering, breadcrumbs, and
resizable panels.

### 3.5 Desktop search

-   Global search --- MUST HAVE
-   Search result table --- MUST HAVE
-   Search filters --- MUST HAVE
-   Full-text results --- SHOULD HAVE
-   Search diagnostics --- OPTIONAL / DEVELOPMENT ONLY

Search should make the source device obvious when results span multiple
devices.

### 3.6 Desktop preview

-   Preview pane/window --- MUST HAVE
-   Image --- MUST HAVE
-   PDF --- MUST HAVE
-   Video --- SHOULD HAVE
-   Audio --- SHOULD HAVE
-   Text --- MUST HAVE
-   Code --- SHOULD HAVE
-   Archive --- SHOULD HAVE
-   Unsupported --- MUST HAVE

### 3.7 Desktop transfers

-   Transfers page --- MUST HAVE
-   Persistent transfer tray --- SHOULD HAVE
-   Transfer detail --- MUST HAVE
-   Queue manager --- MUST HAVE
-   Conflict window --- MUST HAVE

### 3.8 Desktop storage

-   Storage dashboard --- MUST HAVE
-   Storage analytics --- SHOULD HAVE
-   Duplicate analysis --- OPTIONAL

### 3.9 Desktop jobs

-   Jobs dashboard --- SHOULD HAVE
-   Create job --- SHOULD HAVE
-   Job configuration --- SHOULD HAVE
-   Job progress --- SHOULD HAVE
-   Job result --- SHOULD HAVE

### 3.10 Desktop activity

-   Activity page --- MUST HAVE
-   Activity filters --- SHOULD HAVE

### 3.11 Desktop settings

-   General --- MUST HAVE
-   Shared folders --- MUST HAVE
-   Permissions --- MUST HAVE
-   Network --- SHOULD HAVE
-   Transfers --- SHOULD HAVE
-   Search/index --- SHOULD HAVE
-   Notifications --- SHOULD HAVE
-   Security --- MUST HAVE
-   Diagnostics --- SHOULD HAVE
-   Logs --- OPTIONAL / DEVELOPMENT ONLY
-   About --- MUST HAVE

## 4. Reusable shared components

### Device components

-   Device card
-   Device avatar/icon
-   Device status badge
-   Connection badge
-   Last-seen label
-   Device fingerprint block
-   Permission summary
-   Device selector
-   Device switcher
-   Device health indicator

### Connection components

-   Connected indicator
-   Connecting indicator
-   Reconnecting indicator
-   Offline indicator
-   Direct-P2P indicator
-   Relay indicator
-   Latency badge
-   Connection-quality indicator
-   Error banner
-   Retry/reconnect controls

### File components

-   File row
-   Folder row
-   File grid item
-   File type icon
-   File size label
-   Modified-date label
-   Breadcrumb
-   Thumbnail
-   Selection checkbox
-   Selection counter
-   Context menu
-   Metadata panel
-   Status badge
-   Local/remote badge
-   Transfer badge
-   Permission badge

### Search components

-   Search bar
-   Suggestion
-   Recent search
-   Filter chip
-   Filter panel
-   Result row
-   Result grid item
-   Snippet
-   Result count
-   Loading skeleton
-   Empty state
-   Error state
-   Scope selector
-   Device selector
-   File-type selector

### Transfer components

-   Transfer card
-   Transfer row
-   Linear progress
-   Circular progress
-   Speed label
-   ETA label
-   Bytes-transferred label
-   Pause/resume/cancel/retry controls
-   Priority selector
-   Queue
-   Queue handle
-   Transfer status badge
-   Integrity indicator
-   Retry/chunk diagnostic panel
-   Conflict dialog
-   Storage warning

### Preview components

-   Preview header
-   Preview toolbar
-   Image viewer
-   PDF viewer
-   Video player
-   Audio player
-   Text viewer
-   Code viewer
-   Archive summary
-   Unsupported card
-   Preview loading
-   Preview error
-   Preview metadata drawer

### Storage components

-   Usage ring
-   Usage bar
-   Category chart
-   Category row
-   Largest-file row
-   Largest-folder row
-   Duplicate group
-   Free-space indicator
-   Low-storage warning

### Permission components

-   Permission badge
-   Permission toggle
-   Permission group
-   Folder access card
-   Device access card
-   READ/WRITE/UPLOAD/DOWNLOAD/MOVE/COPY/DELETE/PROCESS controls

## 5. Reusable dialogs and sheets

Dialogs: - Confirmation - Delete confirmation - Revoke device - Rename -
Create folder - Conflict - Permission denied - Connection error -
Pairing confirmation - Cancel transfer - Cancel job - Storage full -
Unsupported file - Retry - Device offline - File changed during
transfer - Authentication expired

Sheets: - File actions - Folder actions - Sort - Filter - Send/share -
Device selection - Destination selection - Transfer priority - More
actions

## 6. Required global states

Every major screen/component must define relevant states: - Normal -
Loading - Empty - Error - Offline - Unauthorized - Partially loaded -
Refreshing - Updating - Disabled - Completed

Important edge states: - no Internet - Wi-Fi disconnected - mobile
network only - remote device offline - direct P2P unavailable - relay
active - storage permission revoked - shared folder no longer
accessible - file deleted remotely - file changed during transfer -
storage full - transfer cancelled - transfer resumed - authentication
expired - device revoked - app restarted - index unavailable - preview
unavailable - unsupported type - version mismatch - protocol
incompatibility - timeout

Every error should explain what happened and what the user can do next.

## 7. Complete end-to-end flows

### First launch

`Splash → Welcome → How it works → Device identity → Storage explanation → Shared folders → Home`

### Pairing

`Devices → Pair → QR scanner/show QR → Pair confirmation → Approval → Success → Device detail`

### Phone browsing laptop

`Home → Laptop → Browse → Folder → File → Preview`

### Phone search

`Device → Search → Query → Indexed remote results → Filters → Preview/Download`

### Large download

`File → Download → Destination → Conflict policy → Transfer → Progress → Network interruption → Reconnect → Resume verified offset → Integrity verification → Complete`

### Laptop browsing phone

`Desktop → Devices → Phone → Browse → Pictures → Image → Preview → Download`

### Laptop sending to phone

`Desktop → File → Send → Phone → Destination → Confirm → Transfer → Verify → Complete`

### Remote rename

`Select → Rename → Validate → Remote authorization → Rename → Index update → FILE_UPDATED event`

### Remote delete

`Select → Delete → Confirmation → Authorization → Delete → Index update → Activity log`

### Reconnection

`Connected → Network lost → Reconnecting → P2P negotiation → Authenticate → Restore subscriptions → Restore transfer state → Resume`

## 8. Android MVP screen set

1.  Splash
2.  Welcome
3.  Device setup
4.  Home
5.  Devices
6.  Pair new device
7.  QR scanner
8.  Pair confirmation
9.  Device detail
10. Remote file explorer
11. Search
12. Search results
13. File preview
14. File details
15. Download/upload flow
16. Transfers
17. Transfer detail
18. Shared folders
19. Permissions
20. Settings
21. Activity
22. Revoke device

## 9. Desktop MVP screen set

1.  Startup
2.  First launch
3.  Device setup
4.  Main dashboard
5.  Devices
6.  Pair device
7.  Local explorer
8.  Remote explorer
9.  Search
10. Search results
11. Preview
12. Transfers
13. Storage
14. Activity
15. Shared folders
16. Permissions
17. Settings
18. Diagnostics
19. Revoke device

## 10. Advanced screens

Add after the core system is stable: - Full-text search - Search
diagnostics - Storage analytics - Duplicate analysis - Remote jobs - Job
configuration/results - Remote download worker - Clipboard sharing -
Advanced transfer queue - Detailed network diagnostics - Split preview
workspace - Developer logs - Performance dashboard

## 11. Navigation design rules

1.  Always show the current device context: `Files · My Laptop`.
2.  Make transfer direction obvious: `Phone → Laptop` or
    `Laptop → Phone`.
3.  Explain unavailable permissions.
4.  Never imply that remote search downloaded the whole filesystem.
5.  Make previews feel lightweight through cached/small preview
    requests.
6.  Always expose transfer progress/state.
7.  Make reconnection understandable.
8.  Require confirmation for destructive actions.
9.  Keep advanced functions away from the main dashboard unless
    relevant.
10. Never expose protocol terminology to normal users.

## 12. Responsive design

### Android phone

`Top bar + content + bottom navigation`

### Android tablet / large screen

`Navigation rail + content + optional preview pane`

### Desktop compact

`Sidebar + main`

### Desktop wide

`Sidebar + file list + preview/details`

Desktop must support drag-and-drop, keyboard navigation,
multi-selection, right-click menus, and resizable panes.

## 13. Accessibility

Every screen must consider: - readable text - adequate touch targets -
content descriptions - keyboard navigation - focus states -
screen-reader labels - contrast - status not communicated by color
alone - meaningful error messages

## 14. File icon master list

Use one coherent premium icon family. Do not mix unrelated icon styles.

### Core

-   Generic file
-   Generic document
-   Unknown file
-   Binary file
-   Executable
-   Shortcut/link
-   Temporary file
-   Hidden file
-   Folder
-   Open folder
-   Shared folder
-   Protected folder
-   Locked folder
-   Empty folder

### Documents

-   PDF
-   DOC
-   DOCX
-   XLS
-   XLSX
-   PPT
-   PPTX
-   ODT
-   ODS
-   ODP
-   TXT
-   MD / Markdown
-   LOG
-   RTF
-   CSV
-   EPUB
-   MOBI

### Images

-   JPG/JPEG
-   PNG
-   WEBP
-   GIF
-   BMP
-   TIFF
-   SVG
-   ICO
-   HEIC/HEIF
-   RAW
-   DNG
-   CR2
-   CR3
-   NEF
-   ARW

### Video

-   MP4
-   MKV
-   MOV
-   AVI
-   WEBM
-   FLV
-   WMV
-   MPEG/MPG
-   M4V
-   3GP
-   Generic video
-   Video processing
-   Video preview unavailable

### Audio

-   MP3
-   WAV
-   FLAC
-   AAC
-   M4A
-   OGG
-   OPUS
-   WMA
-   AIFF
-   Generic audio
-   Music
-   Podcast
-   Voice recording

### Archives

-   ZIP
-   7Z
-   RAR
-   TAR
-   GZ
-   BZ2
-   XZ
-   TAR.GZ
-   TAR.XZ
-   Generic archive
-   Encrypted archive
-   Corrupted archive

### Development

-   Kotlin
-   Java
-   JavaScript
-   TypeScript
-   Python
-   C
-   C++
-   C#
-   Go
-   Rust
-   Swift
-   Dart
-   PHP
-   Ruby
-   SQL
-   HTML
-   CSS
-   SCSS/SASS
-   JSX
-   TSX
-   Vue
-   Svelte
-   JSON
-   XML
-   YAML
-   TOML
-   INI
-   ENV
-   GraphQL
-   Gradle
-   Maven
-   npm/package
-   Docker
-   Git
-   GitHub
-   GitLab

### Database

-   SQLite
-   SQL database
-   PostgreSQL
-   MySQL
-   MongoDB
-   Generic database
-   Database backup
-   Database locked
-   Database error

### Android

-   APK
-   AAB
-   Android project
-   Android resource
-   Manifest
-   Gradle
-   Kotlin source
-   Java source

### Creative/design

-   PSD
-   AI
-   XD
-   FIG
-   SKETCH
-   SVG
-   EPS
-   INDD
-   PRPROJ
-   AEP
-   BLEND
-   3D model
-   Font

### 3D/CAD

-   OBJ
-   FBX
-   GLB
-   GLTF
-   STL
-   STEP/STP
-   DWG
-   DXF
-   BLEND

### Fonts

-   TTF
-   OTF
-   WOFF
-   WOFF2
-   Generic font

### System/special

-   Configuration
-   System file
-   Protected system file
-   Script
-   Certificate
-   Key
-   License
-   Backup
-   Log
-   Cache
-   Temporary

## 15. File-state badges

Do not replace the main file icon; use small status badges: -
Downloading - Uploading - Queued - Paused - Completed - Failed - Retry -
Verified - Processing - Preview available - Preview unavailable -
Remote - Local - Shared - Read-only - Locked - Permission denied - New -
Modified - Conflict - Offline

## 16. Device icon list

Types: - Android phone - Android tablet - Laptop - Desktop PC - Generic
computer - Generic tablet - Generic mobile - Windows device - Linux
device - macOS device where supported

States: - Online - Offline - Connecting - Reconnecting - Trusted -
Untrusted - Revoked - Busy - Syncing - Transfer active - Direct
connection - Relay connection

## 17. Action icon list

Navigation: - Back - Forward - Up - Home - Menu - More - Close -
Expand - Collapse

Files: - Open - Preview - Download - Upload - Send - Receive - Rename -
Move - Copy - Delete - Duplicate - Create folder - Refresh - Sort -
Filter - Select all - Deselect - Details - Share - Link - Compress -
Extract

Transfers: - Pause - Resume - Cancel - Retry - Queue - Priority -
Speed - Network - Verify

Security: - Lock - Unlock - Shield - Key - Fingerprint - QR - Trust -
Revoke - Warning - Secure connection

## 18. Search/storage/network/job icons

Search: - Search - Clear - Filter - Sort - Recent search - Search
history - Full-text - Exact match - Search in folder - Search all
devices - Local search - Remote search - Search loading - Search failed

Storage: - Storage - Disk - SSD - HDD - Phone storage - Laptop storage -
Free space - Used space - Low storage - Storage full - Folder size -
Large files - Duplicate files

Network: - Wi-Fi - Mobile data - Ethernet - Internet - Direct P2P -
Relay - Signal strength - Latency - Speed - Upload bandwidth - Download
bandwidth - Disconnected

Jobs: - Jobs - Processing - Queue - Compress/ZIP - Thumbnail
generation - Hash/checksum - PDF preview generation - Image processing -
Video processing - Completed job - Failed job - Cancel job

Notifications: - Notification - Success - Information - Warning -
Error - Transfer complete - Transfer failed - Device connected - Device
disconnected - Job complete - Security alert

## 19. Icon architecture

Do not hardcode one icon for every extension. Use:

`extension → FileTypeResolver → FileCategory → Icon`

Example: `report.pdf → pdf → DOCUMENT → PDF_ICON`

Unknown example: `example.xyz → unknown → GENERIC_FILE + XYZ badge`

This keeps the app extensible.

## 20. Icon collection strategy

Choose one primary premium icon family with consistent: - stroke width -
corner radius - silhouette - perspective - badge style - visual weight

Prioritize collecting: 1. Generic file/folder icons 2. PDF, DOCX, XLSX,
PPTX, TXT, MD 3. JPG, PNG, MP4, MP3, ZIP, APK 4. Kotlin, Java, JS, TS,
Python, JSON, XML, SQL 5. Specialized CAD, 3D, RAW, design, database,
archive types

Always verify the license permits use in the final application. Do not
mix random emoji, neon/futuristic graphics, or unrelated 3D icon packs
into the professional file UI.

## 21. Implementation checklist

Build in this order:

`Design system → Navigation shell → Home/Devices → Pairing → Permissions → Explorer → Search → Preview → Transfers → Storage/Activity/Settings → Advanced features`

For each screen record: - purpose - user actions - components - states -
navigation entry/exit - protocol/backend dependency - implementation
status

Suggested status:
`[ ] Not started  [~] Designing  [>] Implementing  [✓] Implemented  [!] Revision  [-] Removed/Merged`

## 22. Final rule

This document is the **HOMEPORT UI master checklist**. It is
deliberately comprehensive so that no major workflow is forgotten. It is
**not** a command to create every listed item as a separate page.

The actual implementation should continuously ask:

`Does the user need a separate page here?`

If no, merge the function into an existing screen, dialog, sheet, tab,
pane, or contextual action. If yes, create the screen.

The final requirement is not a particular number of pages. The final
requirement is that every important HOMEPORT capability has an obvious,
safe, responsive, and usable path on both Android and laptop/desktop.

# End of HOMEPORT UI Specification
