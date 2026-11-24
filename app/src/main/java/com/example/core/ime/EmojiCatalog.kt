package com.example.core.ime

data class EmojiEntry(
    val char: String,
    val keywords: String,
    val supportsSkinTone: Boolean = false
)

enum class EmojiCategory(val icon: String, val title: String) {
    RECENT("🕒", "Recent"),
    SMILEYS("😀", "Smileys"),
    PEOPLE("👋", "People"),
    ANIMALS("🐾", "Animals"),
    FOOD("☕", "Food"),
    TRAVEL("🕌", "Travel"),
    ACTIVITIES("⚽", "Activity"),
    OBJECTS("📱", "Objects"),
    SYMBOLS("❤️", "Symbols"),
    FLAGS("🇵🇰", "Flags"),
    KAOMOJI("(◕‿◕)", "Stickers")
}

object EmojiCatalog {

    val skinTones = listOf("", "🏻", "🏼", "🏽", "🏾", "🏿")

    val kaomojiAndStickers = listOf(
        "السلام علیکم", "وعلیکم السلام", "شالا خوش رہو", "جی آیاں نوں", "ٻہوں مہربانی",
        "(◕‿◕)", "¯\\_(ツ)_/¯", "(｡♥‿♥｡)", "(ง •̀_•́)ง", "★~(◠‿◕✿)",
        "(•̀ᴗ•́)و ̑̑", "(づ｡◕‿‿◕｡)づ", "(¬‿¬)", "ʕ•ᴥ•ʔ", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧"
    )

    val byCategory: Map<EmojiCategory, List<EmojiEntry>> = mapOf(
        EmojiCategory.SMILEYS to listOf(
            EmojiEntry("😀", "grin happy smile"),
            EmojiEntry("😃", "smile joy happy"),
            EmojiEntry("😄", "laugh happy"),
            EmojiEntry("😁", "beam grin"),
            EmojiEntry("😂", "tears joy laugh lol"),
            EmojiEntry("🤣", "rofl laugh"),
            EmojiEntry("😊", "blush happy khush"),
            EmojiEntry("😍", "heart eyes love pyar"),
            EmojiEntry("🥰", "love hearts"),
            EmojiEntry("😘", "kiss love"),
            EmojiEntry("😉", "wink"),
            EmojiEntry("😎", "cool sunglasses"),
            EmojiEntry("🤩", "star struck"),
            EmojiEntry("🤔", "think"),
            EmojiEntry("😔", "sad sorry"),
            EmojiEntry("😢", "cry sad"),
            EmojiEntry("😭", "sob cry"),
            EmojiEntry("😡", "angry mad"),
            EmojiEntry("😴", "sleep tired"),
            EmojiEntry("🥳", "party celebrate"),
            EmojiEntry("🤗", "hug warm"),
            EmojiEntry("😇", "angel innocent"),
            EmojiEntry("🤐", "quiet zip"),
            EmojiEntry("🙄", "eyeroll")
        ),
        EmojiCategory.PEOPLE to listOf(
            EmojiEntry("👋", "wave hello salam", true),
            EmojiEntry("🤚", "hand raised", true),
            EmojiEntry("👌", "ok fine", true),
            EmojiEntry("✌️", "peace victory", true),
            EmojiEntry("🤞", "fingers crossed luck", true),
            EmojiEntry("👍", "thumbs up good like", true),
            EmojiEntry("👎", "thumbs down bad", true),
            EmojiEntry("👏", "clap applause", true),
            EmojiEntry("🙌", "celebrate hands", true),
            EmojiEntry("🤲", "dua pray salam", true),
            EmojiEntry("🙏", "pray thanks please shukriya", true),
            EmojiEntry("🤝", "handshake deal", true),
            EmojiEntry("💪", "muscle strong power", true),
            EmojiEntry("✍️", "write pen", true),
            EmojiEntry("👀", "eyes look see"),
            EmojiEntry("🧠", "brain smart")
        ),
        EmojiCategory.ANIMALS to listOf(
            EmojiEntry("🐪", "camel desert thal"),
            EmojiEntry("🐎", "horse"),
            EmojiEntry("🦅", "eagle shaheen"),
            EmojiEntry("🕊️", "dove peace"),
            EmojiEntry("🦚", "peacock"),
            EmojiEntry("🐈", "cat"),
            EmojiEntry("🐕", "dog"),
            EmojiEntry("🦁", "lion sher"),
            EmojiEntry("🐐", "goat"),
            EmojiEntry("🐑", "sheep"),
            EmojiEntry("🦋", "butterfly"),
            EmojiEntry("🌹", "rose flower gulab"),
            EmojiEntry("🌻", "sunflower"),
            EmojiEntry("🌾", "wheat harvest fasal"),
            EmojiEntry("🌴", "date palm khajoor"),
            EmojiEntry("🌳", "tree")
        ),
        EmojiCategory.FOOD to listOf(
            EmojiEntry("☕", "tea chai coffee"),
            EmojiEntry("🫖", "teapot chai"),
            EmojiEntry("🫓", "roti flatbread naan"),
            EmojiEntry("🍛", "curry biryani"),
            EmojiEntry("🥭", "mango aam multan"),
            EmojiEntry("🍉", "watermelon"),
            EmojiEntry("🍇", "grapes"),
            EmojiEntry("🍊", "orange malta"),
            EmojiEntry("🍌", "banana"),
            EmojiEntry("🍎", "apple"),
            EmojiEntry("🥛", "milk lassi"),
            EmojiEntry("🍯", "honey"),
            EmojiEntry("🍰", "cake sweet"),
            EmojiEntry("🍫", "chocolate"),
            EmojiEntry("🍿", "popcorn"),
            EmojiEntry("🍕", "pizza")
        ),
        EmojiCategory.TRAVEL to listOf(
            EmojiEntry("🕌", "mosque masjid multan"),
            EmojiEntry("🕋", "kaaba"),
            EmojiEntry("🏠", "home ghar house"),
            EmojiEntry("🏙️", "city shahar"),
            EmojiEntry("🚗", "car gaddi"),
            EmojiEntry("🚌", "bus"),
            EmojiEntry("🚆", "train"),
            EmojiEntry("✈️", "airplane travel"),
            EmojiEntry("🚲", "bicycle"),
            EmojiEntry("⛰️", "mountain"),
            EmojiEntry("🌅", "sunrise"),
            EmojiEntry("🌙", "crescent moon"),
            EmojiEntry("☀️", "sun dhoop"),
            EmojiEntry("🌧️", "rain barish"),
            EmojiEntry("⭐", "star"),
            EmojiEntry("🌈", "rainbow")
        ),
        EmojiCategory.ACTIVITIES to listOf(
            EmojiEntry("🏏", "cricket bat ball"),
            EmojiEntry("⚽", "football soccer"),
            EmojiEntry("🏆", "trophy win"),
            EmojiEntry("🎯", "target goal"),
            EmojiEntry("🎨", "art paint"),
            EmojiEntry("🎵", "music song"),
            EmojiEntry("🎶", "notes music"),
            EmojiEntry("🎤", "mic sing"),
            EmojiEntry("📚", "books study"),
            EmojiEntry("🎉", "celebrate party")
        ),
        EmojiCategory.OBJECTS to listOf(
            EmojiEntry("📱", "phone mobile"),
            EmojiEntry("💻", "laptop computer"),
            EmojiEntry("⌨️", "keyboard"),
            EmojiEntry("🖊️", "pen qalam"),
            EmojiEntry("📖", "book kitab"),
            EmojiEntry("📝", "note memo"),
            EmojiEntry("📷", "camera photo"),
            EmojiEntry("🔔", "bell notification"),
            EmojiEntry("🔒", "lock privacy"),
            EmojiEntry("🔑", "key"),
            EmojiEntry("💡", "idea bulb"),
            EmojiEntry("⏰", "alarm clock time")
        ),
        EmojiCategory.SYMBOLS to listOf(
            EmojiEntry("❤️", "red heart love dil pyar"),
            EmojiEntry("💚", "green heart"),
            EmojiEntry("💙", "blue heart"),
            EmojiEntry("💖", "sparkle heart"),
            EmojiEntry("💔", "broken heart"),
            EmojiEntry("✨", "sparkles"),
            EmojiEntry("🔥", "fire lit"),
            EmojiEntry("💯", "100 perfect"),
            EmojiEntry("✅", "check done"),
            EmojiEntry("❌", "cross cancel"),
            EmojiEntry("⚡", "lightning fast"),
            EmojiEntry("☪️", "star crescent"),
            EmojiEntry("☮️", "peace")
        ),
        EmojiCategory.FLAGS to listOf(
            EmojiEntry("🇵🇰", "pakistan flag"),
            EmojiEntry("🇸🇦", "saudi arabia"),
            EmojiEntry("🇦🇪", "uae"),
            EmojiEntry("🇹🇷", "turkey"),
            EmojiEntry("🇬🇧", "uk britain"),
            EmojiEntry("🇺🇸", "usa america"),
            EmojiEntry("🇨🇦", "canada"),
            EmojiEntry("🇦🇺", "australia"),
            EmojiEntry("🇶🇦", "qatar"),
            EmojiEntry("🇴🇲", "oman")
        )
    )

    fun search(query: String): List<EmojiEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return byCategory.values.flatten().filter { it.keywords.contains(q) }.distinctBy { it.char }
    }
}
