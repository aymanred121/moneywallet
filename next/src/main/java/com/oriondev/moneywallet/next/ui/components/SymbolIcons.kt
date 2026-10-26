package com.oriondev.moneywallet.next.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.ChildFriendly
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PropaneTank
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material Symbols (rounded) offered for categories, wallets and goals, stored as
 * [com.oriondev.moneywallet.next.core.IconSpec.Symbol]. Each icon is referenced explicitly so R8
 * keeps exactly these out of the extended icon set. Keys are persisted: never rename one.
 */
object SymbolIcons {
    data class Symbol(val key: String, val label: String, val vector: ImageVector)

    val groups: List<Pair<String, List<Symbol>>> = listOf(
        "Food & drink" to listOf(
            Symbol("restaurant", "Restaurant", Icons.Rounded.Restaurant),
            Symbol("fastfood", "Fastfood", Icons.Rounded.Fastfood),
            Symbol("local_cafe", "Local Cafe", Icons.Rounded.LocalCafe),
            Symbol("local_pizza", "Local Pizza", Icons.Rounded.LocalPizza),
            Symbol("bakery_dining", "Bakery Dining", Icons.Rounded.BakeryDining),
            Symbol("icecream", "Icecream", Icons.Rounded.Icecream),
            Symbol("lunch_dining", "Lunch Dining", Icons.Rounded.LunchDining),
            Symbol("ramen_dining", "Ramen Dining", Icons.Rounded.RamenDining),
            Symbol("kebab_dining", "Kebab Dining", Icons.Rounded.KebabDining),
            Symbol("set_meal", "Set Meal", Icons.Rounded.SetMeal),
            Symbol("emoji_food_beverage", "Emoji Food Beverage", Icons.Rounded.EmojiFoodBeverage),
            Symbol("cake", "Cake", Icons.Rounded.Cake),
            Symbol("local_grocery_store", "Local Grocery Store", Icons.Rounded.LocalGroceryStore),
            Symbol("egg", "Egg", Icons.Rounded.Egg),
            Symbol("local_dining", "Local Dining", Icons.Rounded.LocalDining),
        ),
        "Shopping" to listOf(
            Symbol("shopping_cart", "Shopping Cart", Icons.Rounded.ShoppingCart),
            Symbol("shopping_bag", "Shopping Bag", Icons.Rounded.ShoppingBag),
            Symbol("local_mall", "Local Mall", Icons.Rounded.LocalMall),
            Symbol("storefront", "Storefront", Icons.Rounded.Storefront),
            Symbol("checkroom", "Checkroom", Icons.Rounded.Checkroom),
            Symbol("diamond", "Diamond", Icons.Rounded.Diamond),
            Symbol("watch", "Watch", Icons.Rounded.Watch),
            Symbol("redeem", "Redeem", Icons.Rounded.Redeem),
            Symbol("card_giftcard", "Card Giftcard", Icons.Rounded.CardGiftcard),
            Symbol("sell", "Sell", Icons.Rounded.Sell),
            Symbol("local_offer", "Local Offer", Icons.Rounded.LocalOffer),
        ),
        "Transport" to listOf(
            Symbol("directions_car", "Directions Car", Icons.Rounded.DirectionsCar),
            Symbol("local_gas_station", "Local Gas Station", Icons.Rounded.LocalGasStation),
            Symbol("ev_station", "Ev Station", Icons.Rounded.EvStation),
            Symbol("local_taxi", "Local Taxi", Icons.Rounded.LocalTaxi),
            Symbol("directions_bus", "Directions Bus", Icons.Rounded.DirectionsBus),
            Symbol("train", "Train", Icons.Rounded.Train),
            Symbol("subway", "Subway", Icons.Rounded.Subway),
            Symbol("two_wheeler", "Two Wheeler", Icons.Rounded.TwoWheeler),
            Symbol("pedal_bike", "Pedal Bike", Icons.Rounded.PedalBike),
            Symbol("local_parking", "Local Parking", Icons.Rounded.LocalParking),
            Symbol("car_repair", "Car Repair", Icons.Rounded.CarRepair),
            Symbol("toll", "Toll", Icons.Rounded.Toll),
        ),
        "Home & bills" to listOf(
            Symbol("home", "Home", Icons.Rounded.Home),
            Symbol("apartment", "Apartment", Icons.Rounded.Apartment),
            Symbol("bolt", "Bolt", Icons.Rounded.Bolt),
            Symbol("water_drop", "Water Drop", Icons.Rounded.WaterDrop),
            Symbol("wifi", "Wifi", Icons.Rounded.Wifi),
            Symbol("phone_android", "Phone Android", Icons.Rounded.PhoneAndroid),
            Symbol("router", "Router", Icons.Rounded.Router),
            Symbol("propane_tank", "Propane Tank", Icons.Rounded.PropaneTank),
            Symbol("receipt", "Receipt", Icons.Rounded.Receipt),
            Symbol("receipt_long", "Receipt Long", Icons.Rounded.ReceiptLong),
            Symbol("chair", "Chair", Icons.Rounded.Chair),
            Symbol("bed", "Bed", Icons.Rounded.Bed),
            Symbol("kitchen", "Kitchen", Icons.Rounded.Kitchen),
            Symbol("handyman", "Handyman", Icons.Rounded.Handyman),
            Symbol("plumbing", "Plumbing", Icons.Rounded.Plumbing),
            Symbol("cleaning_services", "Cleaning Services", Icons.Rounded.CleaningServices),
            Symbol("local_laundry_service", "Local Laundry Service", Icons.Rounded.LocalLaundryService),
            Symbol("yard", "Yard", Icons.Rounded.Yard),
        ),
        "Health & care" to listOf(
            Symbol("local_hospital", "Local Hospital", Icons.Rounded.LocalHospital),
            Symbol("medical_services", "Medical Services", Icons.Rounded.MedicalServices),
            Symbol("medication", "Medication", Icons.Rounded.Medication),
            Symbol("vaccines", "Vaccines", Icons.Rounded.Vaccines),
            Symbol("health_and_safety", "Health And Safety", Icons.Rounded.HealthAndSafety),
            Symbol("fitness_center", "Fitness Center", Icons.Rounded.FitnessCenter),
            Symbol("spa", "Spa", Icons.Rounded.Spa),
            Symbol("psychology", "Psychology", Icons.Rounded.Psychology),
            Symbol("monitor_heart", "Monitor Heart", Icons.Rounded.MonitorHeart),
            Symbol("face", "Face", Icons.Rounded.Face),
            Symbol("content_cut", "Content Cut", Icons.Rounded.ContentCut),
        ),
        "Leisure" to listOf(
            Symbol("movie", "Movie", Icons.Rounded.Movie),
            Symbol("theater_comedy", "Theater Comedy", Icons.Rounded.TheaterComedy),
            Symbol("sports_esports", "Sports Esports", Icons.Rounded.SportsEsports),
            Symbol("sports_soccer", "Sports Soccer", Icons.Rounded.SportsSoccer),
            Symbol("pool", "Pool", Icons.Rounded.Pool),
            Symbol("music_note", "Music Note", Icons.Rounded.MusicNote),
            Symbol("headphones", "Headphones", Icons.Rounded.Headphones),
            Symbol("live_tv", "Live TV", Icons.Rounded.LiveTv),
            Symbol("subscriptions", "Subscriptions", Icons.Rounded.Subscriptions),
            Symbol("celebration", "Celebration", Icons.Rounded.Celebration),
            Symbol("park", "Park", Icons.Rounded.Park),
            Symbol("palette", "Palette", Icons.Rounded.Palette),
            Symbol("camera_alt", "Camera Alt", Icons.Rounded.CameraAlt),
            Symbol("menu_book", "Menu Book", Icons.Rounded.MenuBook),
        ),
        "Travel" to listOf(
            Symbol("flight", "Flight", Icons.Rounded.Flight),
            Symbol("hotel", "Hotel", Icons.Rounded.Hotel),
            Symbol("luggage", "Luggage", Icons.Rounded.Luggage),
            Symbol("beach_access", "Beach Access", Icons.Rounded.BeachAccess),
            Symbol("map", "Map", Icons.Rounded.Map),
            Symbol("public", "Public", Icons.Rounded.Public),
            Symbol("hiking", "Hiking", Icons.Rounded.Hiking),
            Symbol("directions_boat", "Directions Boat", Icons.Rounded.DirectionsBoat),
        ),
        "Work & study" to listOf(
            Symbol("school", "School", Icons.Rounded.School),
            Symbol("auto_stories", "Auto Stories", Icons.Rounded.AutoStories),
            Symbol("science", "Science", Icons.Rounded.Science),
            Symbol("work", "Work", Icons.Rounded.Work),
            Symbol("business_center", "Business Center", Icons.Rounded.BusinessCenter),
            Symbol("computer", "Computer", Icons.Rounded.Computer),
            Symbol("laptop", "Laptop", Icons.Rounded.Laptop),
            Symbol("print", "Print", Icons.Rounded.Print),
            Symbol("engineering", "Engineering", Icons.Rounded.Engineering),
        ),
        "Family & giving" to listOf(
            Symbol("child_care", "Child Care", Icons.Rounded.ChildCare),
            Symbol("child_friendly", "Child Friendly", Icons.Rounded.ChildFriendly),
            Symbol("smart_toy", "Smart Toy", Icons.Rounded.SmartToy),
            Symbol("pets", "Pets", Icons.Rounded.Pets),
            Symbol("favorite", "Favorite", Icons.Rounded.Favorite),
            Symbol("volunteer_activism", "Volunteer Activism", Icons.Rounded.VolunteerActivism),
            Symbol("mosque", "Mosque", Icons.Rounded.Mosque),
            Symbol("people", "People", Icons.Rounded.People),
            Symbol("elderly", "Elderly", Icons.Rounded.Elderly),
        ),
        "Money" to listOf(
            Symbol("account_balance", "Account Balance", Icons.Rounded.AccountBalance),
            Symbol("account_balance_wallet", "Account Balance Wallet", Icons.Rounded.AccountBalanceWallet),
            Symbol("savings", "Savings", Icons.Rounded.Savings),
            Symbol("payments", "Payments", Icons.Rounded.Payments),
            Symbol("credit_card", "Credit Card", Icons.Rounded.CreditCard),
            Symbol("attach_money", "Attach Money", Icons.Rounded.AttachMoney),
            Symbol("currency_exchange", "Currency Exchange", Icons.Rounded.CurrencyExchange),
            Symbol("trending_up", "Trending Up", Icons.Rounded.TrendingUp),
            Symbol("show_chart", "Show Chart", Icons.Rounded.ShowChart),
            Symbol("request_quote", "Request Quote", Icons.Rounded.RequestQuote),
            Symbol("paid", "Paid", Icons.Rounded.Paid),
            Symbol("money_off", "Money Off", Icons.Rounded.MoneyOff),
            Symbol("handshake", "Handshake", Icons.Rounded.Handshake),
            Symbol("gavel", "Gavel", Icons.Rounded.Gavel),
            Symbol("shield", "Shield", Icons.Rounded.Shield),
        ),
        "Other" to listOf(
            Symbol("build", "Build", Icons.Rounded.Build),
            Symbol("local_shipping", "Local Shipping", Icons.Rounded.LocalShipping),
            Symbol("inventory", "Inventory", Icons.Rounded.Inventory),
            Symbol("star", "Star", Icons.Rounded.Star),
            Symbol("label", "Label", Icons.Rounded.Label),
            Symbol("category", "Category", Icons.Rounded.Category),
            Symbol("more_horiz", "More Horiz", Icons.Rounded.MoreHoriz),
        ),
    )

    private val byKey: Map<String, Symbol> by lazy { groups.flatMap { it.second }.associateBy { it.key } }

    fun vector(key: String): ImageVector? = byKey[key]?.vector
    fun label(key: String): String? = byKey[key]?.label

    /**
     * Legacy resource names the bundled icon set never had (the old default categories point at
     * them), drawn with the closest symbol instead of falling back to letters.
     */
    val legacyNames: Map<String, String> = mapOf(
        "ic_icon_shopping_cart" to "shopping_cart", "ic_icon_home" to "home", "ic_icon_receipt" to "receipt",
        "ic_icon_medical" to "medical_services", "ic_icon_shopping_bag" to "shopping_bag", "ic_icon_gamepad" to "sports_esports",
        "ic_icon_airplane" to "flight", "ic_icon_wallet" to "account_balance_wallet", "ic_icon_briefcase" to "work",
        "ic_icon_money" to "payments",
    )
}
