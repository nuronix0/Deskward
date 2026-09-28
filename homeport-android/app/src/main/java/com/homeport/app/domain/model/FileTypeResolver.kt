package com.homeport.app.domain.model

/**
 * Maps file extensions to the 15 universal FileCategory values.
 * Only 15 categories needed — no per-extension icons.
 * Optionally show a badge for well-known types (PDF, APK, ZIP, MP4…).
 */
object FileTypeResolver {

    private val extensionMap: Map<String, FileCategory> = buildMap {
        // ── Documents ──────────────────────────────────────────────────────
        listOf("pdf","doc","docx","odt","rtf","txt","md","markdown","log",
               "epub","mobi","pages","tex","csv","tsv","xlsx","xls","ods",
               "ppt","pptx","odp","key","numbers").forEach { put(it, FileCategory.DOCUMENT) }

        // ── Images ────────────────────────────────────────────────────────
        listOf("jpg","jpeg","png","webp","gif","bmp","tiff","tif","svg",
               "ico","heic","heif","raw","dng","cr2","cr3","nef","arw",
               "raf","orf","rw2","pef","srw","avif").forEach { put(it, FileCategory.IMAGE) }

        // ── Videos ────────────────────────────────────────────────────────
        listOf("mp4","mkv","mov","avi","webm","flv","wmv","mpeg","mpg",
               "m4v","3gp","3g2","ts","m2ts","vob","ogv","rmvb").forEach { put(it, FileCategory.VIDEO) }

        // ── Audio ─────────────────────────────────────────────────────────
        listOf("mp3","wav","flac","aac","m4a","ogg","opus","wma","aiff",
               "aif","mid","midi","amr","ape","dsf","dff").forEach { put(it, FileCategory.AUDIO) }

        // ── Archives ──────────────────────────────────────────────────────
        listOf("zip","7z","rar","tar","gz","bz2","xz","tgz","tbz","zst",
               "cab","iso","dmg","pkg","deb","rpm","apks","xapk").forEach { put(it, FileCategory.ARCHIVE) }

        // ── Code ──────────────────────────────────────────────────────────
        listOf("kt","kts","java","js","ts","jsx","tsx","py","c","cpp","cc",
               "cxx","h","hpp","cs","go","rs","swift","dart","php","rb",
               "sh","bash","zsh","fish","ps1","lua","r","m","scala","ex",
               "exs","clj","hs","ml","fs","vue","svelte","html","htm",
               "css","scss","sass","less","sql","graphql","gql","gradle",
               "groovy","toml","lock","makefile","dockerfile").forEach { put(it, FileCategory.CODE) }

        // ── Database ──────────────────────────────────────────────────────
        listOf("db","sqlite","sqlite3","sql","mdb","accdb","dbf","pgdump",
               "dump","bak","realm").forEach { put(it, FileCategory.DATABASE) }

        // ── Applications ──────────────────────────────────────────────────
        listOf("apk","aab","xapk","exe","msi","app","dmg","deb","rpm",
               "appimage","flatpak","snap","ipa","jar","war","ear",
               "class","pyc","wasm").forEach { put(it, FileCategory.APPLICATION) }

        // ── Design ────────────────────────────────────────────────────────
        listOf("psd","ai","fig","xd","sketch","eps","indd","prproj","aep",
               "afdesign","afphoto","afpub","cdr","xcf","blend").forEach { put(it, FileCategory.DESIGN) }

        // ── 3D / CAD ──────────────────────────────────────────────────────
        listOf("obj","fbx","glb","gltf","stl","step","stp","dwg","dxf",
               "3ds","dae","ply","x3d","abc","usd","usda","usdc").forEach { put(it, FileCategory.MODEL_3D) }

        // ── Fonts ─────────────────────────────────────────────────────────
        listOf("ttf","otf","woff","woff2","eot","fon","pfb","pfm").forEach { put(it, FileCategory.FONT) }

        // ── Config ────────────────────────────────────────────────────────
        listOf("json","xml","yaml","yml","toml","ini","cfg","conf","env",
               "properties","plist","reg","htaccess","gitignore",
               "gitconfig","editorconfig","eslintrc","prettierrc",
               "babelrc","npmrc").forEach { put(it, FileCategory.CONFIG) }

        // ── Security ──────────────────────────────────────────────────────
        listOf("pem","cer","crt","der","p12","pfx","p7b","p7c","key",
               "pub","gpg","pgp","asc","jks","bks","keystore",
               "enc","encrypted").forEach { put(it, FileCategory.SECURITY) }

        // ── System ────────────────────────────────────────────────────────
        listOf("sys","dll","so","dylib","lib","a","ko","bin","dat","hex",
               "rom","fw","boot","efi","drv","cpl","ocx","ax").forEach { put(it, FileCategory.SYSTEM) }
    }

    /** Badges shown for high-recognition extensions */
    private val badgeExtensions = setOf(
        "pdf","apk","zip","mp4","mp3","docx","xlsx","pptx","rar","7z",
        "jpg","png","gif","exe","iso","dmg","aab","flac","mkv","tar"
    )

    fun resolve(fileName: String): FileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return extensionMap[ext] ?: FileCategory.UNKNOWN
    }

    fun resolve(category: FileCategory): FileCategory = category

    fun shouldShowBadge(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in badgeExtensions
    }

    fun getBadgeLabel(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext in badgeExtensions) ext.uppercase() else null
    }
}
