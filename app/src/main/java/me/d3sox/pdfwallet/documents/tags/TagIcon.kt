package me.d3sox.pdfwallet.documents.tags

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag

/**
 * The icons a tag can have. They are stored by name, so entries can be added and reordered, but not renamed.
 */
enum class TagIcon(@DrawableRes val drawable: Int, @StringRes val label: Int) {
    TRAIN(R.drawable.tag_icon_train, R.string.tag_icon_train),
    FLIGHT(R.drawable.tag_icon_flight, R.string.tag_icon_flight),
    BUS(R.drawable.tag_icon_directions_bus, R.string.tag_icon_bus),
    CAR(R.drawable.tag_icon_directions_car, R.string.tag_icon_car),
    BOAT(R.drawable.tag_icon_directions_boat, R.string.tag_icon_boat),
    BIKE(R.drawable.tag_icon_pedal_bike, R.string.tag_icon_bike),
    TICKET(R.drawable.tag_icon_confirmation_number, R.string.tag_icon_ticket),
    MOVIE(R.drawable.tag_icon_movie, R.string.tag_icon_movie),
    MUSIC(R.drawable.tag_icon_music_note, R.string.tag_icon_music),
    STADIUM(R.drawable.tag_icon_stadium, R.string.tag_icon_stadium),
    MUSEUM(R.drawable.tag_icon_museum, R.string.tag_icon_museum),
    HOTEL(R.drawable.tag_icon_hotel, R.string.tag_icon_hotel),
    RESTAURANT(R.drawable.tag_icon_restaurant, R.string.tag_icon_restaurant),
    HEALTH(R.drawable.tag_icon_local_hospital, R.string.tag_icon_health),
    WORK(R.drawable.tag_icon_work, R.string.tag_icon_work),
    SCHOOL(R.drawable.tag_icon_school, R.string.tag_icon_school),
    SHOPPING(R.drawable.tag_icon_shopping_bag, R.string.tag_icon_shopping),
    HOME(R.drawable.tag_icon_home, R.string.tag_icon_home),
    STAR(R.drawable.tag_icon_star, R.string.tag_icon_star),
    HEART(R.drawable.tag_icon_favorite, R.string.tag_icon_heart),
}

val Tag.tagIcon: TagIcon? get() = TagIcon.entries.firstOrNull { it.name == icon }

/** The icon of the tag, or a generic tag icon if none was chosen */
@get:DrawableRes
val Tag.iconDrawable: Int get() = tagIcon?.drawable ?: R.drawable.tag_24

/**
 * The icon of [tag], see [iconDrawable].
 */
@Composable
fun TagIconView(
    tag: Tag,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painterResource(tag.iconDrawable),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
