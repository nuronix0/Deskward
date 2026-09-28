# HOMEPORT — Dark Liquid Glass UI Kit
## Home Dashboard + Reusable Component System

**Purpose:** Visual UI kit for HOMEPORT, derived from the HOMEPORT UI specification and implementation plan, but with the visual language reinterpreted around the supplied Apple-inspired dark Liquid Glass reference.

**Primary visual reference:** the supplied black/graphite Apple-style glass UI with soft blur, subtle reflection, rounded continuous surfaces, restrained highlights, and minimal typography.

---

# 01 — DESIGN DIRECTION

## Style name

**Dark Liquid Glass — Apple-inspired spatial UI**

Do NOT treat this as ordinary Glassmorphism.

The visual language should feel like:
- black/graphite environment
- translucent smoked-glass surfaces
- soft background diffusion
- subtle internal reflection
- thin illuminated edges
- controlled depth
- very restrained shadows
- large continuous corner radii
- minimal Apple-like typography
- almost no decorative gradients
- no neon/futuristic cyberpunk styling
- no generic SaaS card styling

The reference has glass that is **dark and physical**, not transparent acrylic. Background content is only partially visible through the material.

## Visual hierarchy

1. Background / atmosphere
2. Primary glass surfaces
3. Secondary glass controls
4. Fine edge highlights
5. Typography
6. Small status illumination
7. Soft Emerald accent only where interaction or state requires it

The interface should feel calm, expensive, quiet and spatial.


## Accent philosophy — Obsidian + Soft Emerald

HOMEPORT should not use blue as its primary brand accent. Blue/cyan makes the product read as a conventional networking or SaaS interface. The preferred identity is **black/graphite + white/silver + restrained Soft Emerald**.

Target visual balance:
- 85–90% black/graphite
- 8–12% white/silver typography and highlights
- 1–3% Soft Emerald accent

Emerald should appear as illumination, state, selection, progress or confirmation—not as a large decorative color field.

---

# 02 — HOMEPORT PRODUCT MODEL

HOMEPORT is a private bidirectional device network.

Phone and laptop are peers.

Core user model:

`Pair → Trust → Authorize → Connect → Browse/Search → Preview → Transfer → Manage → Monitor → Recover → Revoke`

The UI should hide WebRTC, STUN/TURN, signaling, chunking, indexing and integrity complexity from normal users.

Home is the command center for:
- connected devices
- connection state
- storage overview
- recent files
- recent transfers
- important actions

The HOMEPORT specification explicitly defines Home as a primary Android destination and says the Home dashboard should show connected devices, connection state, storage summary, recent files, recent transfers and important actions.

---

# 03 — HOME DASHBOARD

## Screen structure

Android phone:

```text
┌─────────────────────────────┐
│ HOMEPORT              •••   │
│                             │
│ Good evening                │
│ Your devices are ready      │
│                             │
│ ┌─────────────────────────┐ │
│ │ Connected Devices        │ │
│ │                         │ │
│ │  ◉ My Laptop            │ │
│ │    Online · 482 GB free │ │
│ │                         │ │
│ │  ◉ My Phone             │ │
│ │    Online · 71 GB free  │ │
│ └─────────────────────────┘ │
│                             │
│ ┌─────────────────────────┐ │
│ │ My Laptop               │ │
│ │ Storage                 │ │
│ │                         │ │
│ │        742 GB           │ │
│ │       of 1 TB           │ │
│ │                         │ │
│ │ [ Browse ] [ Search ]   │ │
│ └─────────────────────────┘ │
│                             │
│ Recent Files                │
│ ┌─────────────────────────┐ │
│ │ PDF  Project Report      │ │
│ │     4.8 MB · 2h ago     │ │
│ └─────────────────────────┘ │
│                             │
│ Recent Transfers            │
│ ┌─────────────────────────┐ │
│ │ ↑ video.mp4             │ │
│ │   72% · 18.2 MB/s       │ │
│ └─────────────────────────┘ │
│                             │
│ Home  Devices  Transfers ...│
└─────────────────────────────┘
```

Do not literally copy the ASCII layout. Use it as information architecture.

---

# 04 — HOME HEADER

## Header

Components:
- HOMEPORT wordmark
- optional connection/global status indicator
- overflow button
- optional profile/device identity button

### Visual treatment

Header should be visually light.

Do NOT put the entire header inside a heavy card.

Use:
- transparent background
- 0–4% white surface tint
- subtle bottom diffusion only when required
- white primary text
- muted secondary text
- compact icon buttons

### Typography

Title:
- 24–28 px
- semibold
- tight tracking

Secondary:
- 14–16 px
- regular
- reduced opacity

---

# 05 — GLASS MATERIAL SYSTEM

## Material A — Primary Dark Glass

Use for:
- device cards
- storage cards
- transfer cards
- major dashboard modules

Properties:
- near-black translucent fill
- medium-high backdrop blur
- low saturation
- very subtle white/gray inner highlight
- 1 px soft border
- soft ambient shadow
- continuous 24–32 px radius

The surface must remain readable against a black background.

## Material B — Secondary Glass

Use for:
- search field
- compact controls
- filter chips
- status capsules
- segmented controls
- small action containers

Properties:
- darker translucent fill
- lower blur than primary surfaces
- subtle border
- 16–22 px radius

## Material C — Elevated Glass

Use for:
- dialogs
- bottom sheets
- context menus
- expanded transfer controls

Properties:
- slightly brighter surface
- stronger blur
- more visible rim highlight
- stronger separation from background
- 24–30 px radius

## Material D — Glass Button

Use for:
- secondary actions
- icon buttons
- compact controls

Properties:
- smoked translucent surface
- subtle specular highlight
- 12–18 px radius
- tactile pressed state

## Material E — Solid Action

For the most important CTA:
- use a near-white solid surface
- black text
- high contrast
- no heavy gradient
- large pill/rounded rectangle
- subtle shadow

This follows the supplied reference where primary actions are bright, simple and extremely legible.

---

# 06 — GLASS EFFECT RECIPE

Every major glass surface should combine:

```text
Translucency
+
Backdrop blur
+
Subtle background refraction/diffusion
+
1px edge highlight
+
Very soft inner highlight
+
Controlled shadow
+
Large continuous radius
```

Avoid:
- strong white borders
- excessive transparency
- rainbow refraction
- neon glow
- large drop shadows
- frosted-white cards
- excessive gradients
- glowing outlines
- generic "crypto dashboard" aesthetics

The glass should be noticed only after looking closely.

---

# 07 — BACKGROUND

## Base

Use a deep graphite/black environment.

Suggested hierarchy:
- outer background: near-black graphite
- secondary atmospheric layer: slightly lighter graphite
- optional extremely subtle radial light fields
- optional soft blurred device/storage imagery behind selected glass surfaces

Do not use a flat pure black everywhere.

The reference has depth created by very subtle tonal variation.

## Background lighting

Allowed:
- soft gray illumination
- very low-intensity neutral/emerald atmospheric light
- restrained soft-emerald light behind active/success states

Not allowed:
- neon blue/purple glow or cyan-heavy lighting
- cyberpunk lighting
- colorful gradients covering the UI

---

# 08 — COLOR TOKENS

The visual system should remain mostly monochrome.

### Core — Obsidian + Soft Emerald

The interface is intentionally 85–90% monochrome. Emerald is an accent, not a surface color.

```text
Background / Obsidian
#080909

Graphite
#111214

Raised Graphite
#17191B

Deep Glass
rgba(255,255,255,0.045)

Glass White
rgba(255,255,255,0.06)

Glass Highlight
rgba(255,255,255,0.10)

Border
rgba(255,255,255,0.10)

Primary Text
#F5F5F7

Secondary Text
rgba(245,245,247,0.62)

Tertiary Text
rgba(245,245,247,0.38)

Divider
rgba(255,255,255,0.07)

Primary Action
#F5F5F5

Primary Action Text
#090909
```

### Accent palette

```text
Soft Emerald / Primary Accent
#7DD6B0

Emerald Dark
#2D6A56

Emerald Glow
rgba(125,214,176,0.18)

Emerald Glass
rgba(125,214,176,0.08)

Emerald Border
rgba(125,214,176,0.22)
```

Use Soft Emerald for:
- connected-device indicators
- active navigation
- transfer progress
- selected controls
- storage highlights
- successful actions
- subtle ambient reflection

Do not tint entire cards emerald. The glass remains black/graphite.

### Semantic states

```text
Connected / Success
#7DD6B0

Connecting / Updating
#E6B86A

Degraded / Warning
#E6B86A

Offline / Error
#E87575

Information / Active
#7DD6B0
```

State colors should illuminate small semantic areas rather than recolor entire components.


---

# 09 — TYPOGRAPHY

Use an Apple-like neutral sans-serif.

Preferred:
- SF Pro / system sans where available
- Inter as a cross-platform fallback

Hierarchy:

```text
Display       30–36 px / semibold
Screen title  24–28 px / semibold
Section title 17–20 px / semibold
Card title    16–18 px / medium
Body          14–16 px / regular
Metadata      12–14 px / regular
Micro label   11–12 px / medium
```

Avoid:
- oversized marketing headings
- condensed fonts
- futuristic fonts
- excessive letter spacing
- all-caps UI except tiny labels where necessary

---

# 10 — SPACING SYSTEM

Use an 8 px base rhythm.

```text
4   micro
8   compact
12  small
16  default
20  comfortable
24  card padding
32  section spacing
40  major separation
48  screen-level separation
```

Android screen horizontal padding:
- 20–24 px

Card internal padding:
- 18–24 px

Between major cards:
- 12–16 px

---

# 11 — CORNER RADIUS SYSTEM

Use large continuous Apple-like radii.

```text
8   micro controls
12  small controls
16  compact cards
20  secondary cards
24  standard cards
28  major glass cards
32  hero surfaces
999 pill
```

Avoid mixing many unrelated radii.

---

# 12 — SHADOW + EDGE SYSTEM

Shadows must be subtle.

Primary glass:
- low opacity
- large blur
- short-to-medium spread

Elevated glass:
- slightly stronger shadow
- never black "cutout" shadow

Edge treatment:
- 1 px translucent border
- optional top/upper-left highlight
- optional inner highlight

Do not use thick outlines.

---

# 13 — ICON SYSTEM

Use one coherent premium icon family.

Preferred characteristics:
- thin/medium stroke
- rounded terminals
- consistent visual weight
- simple silhouettes
- no emoji
- no random 3D icons
- no neon icon packs

Required Home icon groups:
- device
- laptop
- phone
- storage
- folder
- file
- search
- transfer
- upload
- download
- connection
- Wi-Fi/network
- activity
- settings
- more
- refresh
- warning
- success
- offline

The source UI specification requires one coherent premium icon family and specifically warns against mixing random emoji, neon/futuristic graphics or unrelated 3D packs.

---

# 14 — DEVICE CARD

## Purpose

Primary Home component representing a trusted peer.

### Content

```text
Device icon
My Laptop
Windows · Online

482 GB available

[Browse]     [•••]
```

Optional:
- connection quality
- last seen
- storage mini-meter
- transfer activity

### States

1. Online
2. Connecting
3. Reconnecting
4. Degraded
5. Offline
6. Revoked
7. Busy
8. Syncing
9. Transfer active

### Online visual

- tiny status light
- soft localized green illumination
- mostly monochrome card

### Offline visual

Do NOT turn the whole card red.

Use:
- muted status dot
- reduced contrast
- "Offline" label
- optional "Last seen ..." metadata

---

# 15 — DEVICE CARD ANATOMY

```text
┌────────────────────────────────┐
│ ◉  My Laptop               ••• │
│    Windows · Online             │
│                                 │
│    482 GB available             │
│                                 │
│    Storage  ███████░░░ 74%      │
│                                 │
│    [ Browse ]       [ Search ]  │
└────────────────────────────────┘
```

The card should feel like a physical glass object rather than a flat database row.

---

# 16 — STORAGE CARD

## Content

Show:
- device
- total storage
- used storage
- free storage
- usage percentage
- compact usage visualization

Example:

```text
My Laptop

742 GB used
258 GB free

██████████████░░░░ 74%

1 TB total
```

Optional category preview:
- Documents
- Videos
- Pictures
- Projects
- Other

Do not overcrowd Home with full analytics. Advanced analytics belong to Storage.

---

# 17 — STORAGE VISUALIZATION

Preferred:
- circular usage ring
- soft monochrome progress ring
- subtle state illumination
- small center value

Alternative:
- thin horizontal glass progress bar

Avoid:
- pie charts with many colors
- dashboard-style colored graphs
- thick neon rings

---

# 18 — PRIMARY ACTION BUTTON

Reference-inspired solid CTA.

Example:

```text
┌────────────────────────────┐
│          Browse            │
└────────────────────────────┘
```

Properties:
- light solid fill
- black text
- 48–54 px height
- pill or 16–18 px radius
- strong legibility
- subtle press animation

Use for:
- Browse
- Search
- Pair device
- Download
- Send
- Retry

---

# 19 — SECONDARY BUTTON

Dark glass button.

Example:

```text
┌────────────────────────────┐
│  Search                    │
└────────────────────────────┘
```

Properties:
- translucent graphite
- thin border
- white text
- 44–50 px height
- 14–18 px radius

---

# 20 — ICON BUTTON

Use for:
- more
- refresh
- back
- close
- settings
- filter
- sort
- view toggle

Recommended:
- 40–48 px touch target
- 16–18 px icon
- dark glass circular/rounded container
- subtle pressed state

Never sacrifice the touch target to make the icon visually small.

---

# 21 — SEARCH BAR

The search bar is one of the most important reusable components.

```text
┌─────────────────────────────────┐
│  ◯  Search files...          ⌕ │
└─────────────────────────────────┘
```

Style:
- dark glass
- 48–54 px height
- 16–18 px radius
- soft blur
- no heavy border
- search icon on leading edge
- clear button when text exists

Expanded state may show:
- scope
- device
- recent searches
- filter chips

---

# 22 — RECENT FILE ROW

Content:

```text
[FILE ICON]  Project Report.pdf
             PDF · 4.8 MB
             My Laptop · 2h ago
                              •••
```

Use:
- 52–64 px row height
- glass or transparent list surface
- small file icon container
- strong filename
- muted metadata
- trailing context action

Do not put every row inside a separate floating card. Use grouped glass surfaces when possible.

---

# 23 — RECENT TRANSFER CARD

Example:

```text
↑  video.mp4
   My Laptop → My Phone

   72%
   ███████████████░░░░

   3.4 GB / 4.7 GB
   18.2 MB/s · ETA 1m 12s

                         Pause   •••
```

Transfer information must expose:
- direction
- filename
- progress
- bytes
- speed
- ETA when reliable
- state
- available actions

This follows the UI specification.

---

# 24 — QUICK ACTIONS

Home may provide a compact quick-action group.

Recommended:
- Browse
- Search
- Send
- Receive
- Pair device

Do not create five oversized colorful tiles.

Preferred treatment:
- compact horizontal glass controls
- icon + label
- subtle active illumination

---

# 25 — CONNECTION STATUS

Global states:

```text
Connected
Connecting
Reconnecting
Degraded
Offline
```

Visual language:
- connected = tiny soft-emerald illumination
- connecting = subtle amber pulse
- reconnecting = restrained animated indicator
- degraded = amber/orange micro-accent
- offline = muted gray/red indicator

Connection details may show:
- Direct P2P
- Relay connection
- latency
- connection quality

Keep technical networking terminology behind diagnostics unless relevant.

---

# 26 — HOME ACTIVITY PREVIEW

Optional compact section:

```text
Recent Activity

● Laptop connected
  2 min ago

● Project.pdf downloaded
  14 min ago

● Phone paired
  Yesterday
```

Use a timeline-like list, not a large analytics chart.

---

# 27 — SECTION HEADER

Pattern:

```text
Recent Files                         See All
```

Typography:
- 17–19 px
- medium/semibold
- secondary action 13–14 px

Keep section headers simple.

---

# 28 — BOTTOM NAVIGATION

Android primary destinations from the HOMEPORT specification:

```text
Home
Devices
Transfers
Activity
Settings
```

Visual treatment:
- floating or softly integrated dark glass bar
- rounded capsule/large continuous radius
- 5 destinations maximum
- active item uses subtle elevated glass + small illumination
- inactive items remain quiet

Do NOT use a bright colored active pill.

Suggested active state:

```text
       ┌───────────┐
       │  ◉ Home   │
       └───────────┘
```

with a very subtle internal highlight.

---

# 29 — HOME EMPTY STATE

When no devices exist:

```text
No devices yet

Connect your phone or laptop to
start using HOMEPORT privately.

[ Pair a device ]
```

Visual:
- large minimal device/network symbol
- lots of empty space
- one primary CTA
- no illustration clutter

The specification requires a no-devices empty state.

---

# 30 — DEVICE OFFLINE HOME STATE

Example:

```text
My Laptop

Offline
Last seen 12 min ago

[ Retry connection ]
```

Do not remove the device card.

The user should still be able to:
- see device identity
- see last known information
- retry
- open device details

---

# 31 — LOADING STATE

Use skeleton glass surfaces.

Example:

```text
██████████████
████████
████████████████
```

Skeletons:
- low contrast
- slow restrained shimmer
- no bright animated gradients

For remote data, render cached metadata first where available, then refresh.

This follows HOMEPORT's metadata-first and cache-oriented architecture.

---

# 32 — ERROR STATE

Never display only:

`Something went wrong`

Instead:

```text
Couldn't connect to My Laptop

The connection was interrupted.

[ Try Again ]

Last successful connection
2 minutes ago
```

Every error should explain:
1. what happened
2. what the user can do next

---

# 33 — GLOBAL GLASS DIALOG

Dialog structure:

```text
┌──────────────────────────────┐
│ Rename file                  │
│                              │
│ [ Project-final.pdf       ]  │
│                              │
│ Cancel            Rename     │
└──────────────────────────────┘
```

Use:
- elevated glass
- strong backdrop blur
- large radius
- clear hierarchy
- one primary action
- one secondary action

---

# 34 — BOTTOM SHEET

Use for:
- file actions
- folder actions
- sort
- filter
- send/share
- device selection
- destination selection
- transfer priority
- more actions

Sheet:
- elevated glass
- large top corners
- drag indicator
- compact rows
- clear destructive-action separation

---

# 35 — STATUS BADGES

Use compact capsules.

Examples:

```text
● Online
● Offline
↑ Uploading
↓ Downloading
✓ Verified
! Conflict
⌁ Processing
```

Keep badges mostly monochrome.

Only the semantic status accent should change.

---

# 36 — FILE ICON CONTAINERS

File icons should sit inside subtle glass/graphite containers.

Example:

```text
┌──────┐
│ PDF  │
└──────┘
```

Use the master file-type system defined by HOMEPORT:
- generic
- documents
- images
- video
- audio
- archives
- development
- database
- Android
- creative/design
- 3D/CAD
- fonts
- system/special

Use:

`extension → FileTypeResolver → FileCategory → Icon`

Do not hardcode one icon per extension.

---

# 37 — HOME CARD COMPOSITION RULE

Do not build the screen as a collection of identical cards.

Use three levels:

### Level 1 — Primary spatial surfaces
- connected device
- current device storage

### Level 2 — Supporting grouped surfaces
- recent files
- recent transfers

### Level 3 — Lightweight controls
- search
- quick actions
- navigation

This prevents the "dashboard full of floating rectangles" appearance.

---

# 38 — DEPTH MODEL

Use depth through:
- transparency
- blur
- edge highlights
- soft overlap
- background light
- scale/position hierarchy

Not through:
- heavy shadows
- 3D bevels
- thick borders
- skeuomorphic textures

The supplied reference is flat enough to feel modern while still having physical depth.

---

# 39 — MOTION

Motion should be subtle.

Recommended:
- 180–280 ms micro-interactions
- 300–450 ms card transitions
- spring-like easing for sheets
- small scale reduction on press
- opacity/blur transitions
- progress updates without visual jitter

Examples:
- device connects → status light softly appears
- card opens → expands from its position
- bottom sheet → rises with depth
- transfer completes → progress resolves into a check
- connection drops → status changes without dramatic animation

Avoid:
- bouncing UI
- exaggerated parallax
- constant floating animations
- flashy transitions

---

# 40 — HOME RESPONSIVE VARIANTS

## Android phone

```text
Top bar
↓
Connected devices
↓
Storage
↓
Recent files
↓
Recent transfers
↓
Bottom navigation
```

## Tablet

```text
Navigation rail
        +
Main content
        +
Optional secondary device/preview pane
```

## Desktop

Use:

```text
Sidebar
│
├─ Home
├─ Devices
├─ Files
├─ Search
├─ Transfers
├─ Jobs
├─ Storage
├─ Activity
└─ Settings

Main content
+
optional preview/details pane
+
persistent transfer tray
```

The desktop specification requires a sidebar/main-content shell and supports a preview/details pane and persistent transfer tray.

---

# 41 — DESKTOP HOME

Desktop Home should use the same visual language but with more breathing room.

Suggested layout:

```text
┌────────────┬──────────────────────────────────────────┐
│ HOMEPORT   │ Home                              •••   │
│            │                                          │
│ Home       │ Connected Devices                        │
│ Devices    │ ┌────────────┐ ┌────────────┐           │
│ Files      │ │ My Laptop  │ │ My Phone   │           │
│ Search     │ └────────────┘ └────────────┘           │
│ Transfers  │                                          │
│ Jobs       │ Storage + Recent Files + Transfers       │
│ Storage    │                                          │
│ Activity   │                                          │
│ Settings   │                                          │
└────────────┴──────────────────────────────────────────┘
```

Do not simply stretch the phone layout across the desktop.

---

# 42 — TRANSFER TRAY

Desktop persistent component.

Compact glass tray:

```text
┌────────────────────────────────────────────┐
│ ↑ video.mp4        72%     18.2 MB/s  ›   │
└────────────────────────────────────────────┘
```

Clicking expands into transfer details.

---

# 43 — COMPONENT INVENTORY

## Navigation
- top bar
- bottom navigation
- desktop sidebar
- navigation rail
- breadcrumbs
- device switcher

## Buttons
- primary solid
- secondary glass
- tertiary text
- icon button
- destructive
- loading
- disabled

## Device
- device card
- device avatar
- status badge
- connection badge
- last-seen label
- device selector
- device switcher
- health indicator

## Files
- file row
- folder row
- file grid item
- file icon
- thumbnail
- metadata
- selection state
- context menu
- local/remote badge
- permission badge
- transfer badge

## Search
- search bar
- suggestions
- recent search
- filter chip
- filter panel
- result row
- result grid item
- snippet
- result count
- loading skeleton
- empty state
- error state
- scope selector
- device selector
- file-type selector

## Transfers
- transfer card
- transfer row
- linear progress
- circular progress
- speed
- ETA
- bytes
- pause/resume/cancel/retry
- priority
- queue
- transfer status
- integrity indicator
- conflict dialog
- storage warning

## Storage
- usage ring
- usage bar
- category row
- largest-file row
- largest-folder row
- free-space indicator
- low-storage warning

## Permissions
- permission badge
- permission toggle
- permission group
- folder access card
- device access card

## Feedback
- toast
- banner
- dialog
- bottom sheet
- loading
- success
- warning
- error
- offline
- empty

---

# 44 — REQUIRED STATES FOR EVERY COMPONENT

At minimum:

```text
Normal
Loading
Empty
Error
Offline
Unauthorized
Partially loaded
Refreshing
Updating
Disabled
Completed
```

Relevant components should additionally support:
- connecting
- reconnecting
- paused
- failed
- retrying
- verified
- conflict
- permission denied
- storage full

---

# 45 — ACCESSIBILITY

Even with the visual Liquid Glass treatment:

- maintain readable text contrast
- use adequate touch targets
- provide content descriptions
- support keyboard navigation on desktop
- expose focus states
- never communicate state by color alone
- keep error messages meaningful

Do not reduce accessibility to make the glass effect stronger.

---

# 46 — HOMEPORT VISUAL DO / DON'T

## DO

- black/graphite foundation
- restrained glass
- soft blur
- subtle reflection
- continuous rounded corners
- thin borders
- Apple-like simplicity
- monochrome hierarchy
- small soft-emerald functional accents
- generous whitespace
- premium system icons
- clear device context
- obvious transfer direction

## DON'T

- generic glassmorphism
- blue/cyan neon UI
- cyberpunk
- excessive glow
- giant gradients
- colorful analytics dashboards
- random 3D icons
- emoji file icons
- excessive floating cards
- thick borders
- excessive shadows
- excessive transparency
- cramped layouts
- tiny touch targets
- exposing protocol terminology to normal users

---

# 47 — EXACT VISUAL PROMPT FOR UI GENERATION

Use this paragraph when passing the UI kit to an AI design/code generator:

> Build HOMEPORT using a premium Apple-inspired Dark Liquid Glass design language. Use a deep black/graphite environment with smoked translucent glass surfaces, subtle backdrop blur, soft background diffusion, delicate specular reflections, extremely thin edge highlights, controlled depth and large continuous rounded corners. The material should feel like dark physical glass rather than generic frosted glass. Keep the interface predominantly monochrome with white/silver typography and a restrained Soft Emerald (#7DD6B0) accent used only for active, connected, selected, progress and success states. Use Apple-like system typography, generous spacing, minimal visual noise, premium rounded iconography, subtle motion and strong hierarchy. Primary actions may use clean near-white solid buttons with black text, inspired by the supplied reference. Avoid generic SaaS dashboards, blue/cyan-heavy branding, neon, cyberpunk, excessive gradients, excessive glow, colorful cards, thick borders, heavy shadows, random 3D icons and emoji. The result must feel calm, expensive, modern, spatial and extremely polished.

---

# 48 — HOME SCREEN FINAL COMPOSITION

The final Android Home should prioritize this visual order:

```text
HOMEPORT
Global connection status

Connected Devices
    ↓
Large premium device glass card(s)

Storage
    ↓
Current/selected device storage glass surface

Recent Files
    ↓
Lightweight grouped file list

Recent Transfers
    ↓
Compact transfer glass surface

Bottom Navigation
```

The Home screen must feel like a **personal device control center**, not a generic file manager and not a cloud-storage dashboard.

---

# 49 — SOURCE-OF-TRUTH FUNCTIONAL REQUIREMENTS

This visual kit does not replace the HOMEPORT functional specification.

The functional source requires:
- Home dashboard
- connected devices
- connection state
- storage summary
- recent files
- recent transfers
- important actions
- no-device empty state
- device offline state

It also defines reusable device, connection, file, search, transfer, preview, storage and permission components.

The implementation plan describes the Android Home concept as:
- HOMEPORT header
- connected devices
- recent files
- recent transfers
- connection status

It also establishes that HOMEPORT is bidirectional and that phone and laptop are peers.

The visual system in this document changes the **presentation**, not those functional requirements.

---

# 50 — IMPLEMENTATION PRIORITY

Build the design system in this order:

```text
1. Background + atmosphere
2. Typography
3. Glass materials
4. Buttons
5. Icon buttons
6. Navigation
7. Device card
8. Storage card
9. File row
10. Transfer card
11. Search bar
12. Dialog
13. Bottom sheet
14. States
15. Home dashboard
16. Responsive desktop shell
```

Do not start by designing dozens of pages.

First establish the material system and reusable components, then compose the Home dashboard.

---

# 51 — FINAL DESIGN PRINCIPLE

HOMEPORT should look like:

**"A private personal device network designed by Apple if it were presented as a dark, high-end utility."**

It should NOT look like:

**"A file manager with glass cards."**

The distinction is the combination of:
- restraint
- depth
- material quality
- typography
- spacing
- hierarchy
- subtle reflection
- purposeful motion
- almost monochrome color
- minimal chrome

The glass is the material — not the decoration. The visual identity is Obsidian Graphite + Soft Emerald, not blue tech styling.
