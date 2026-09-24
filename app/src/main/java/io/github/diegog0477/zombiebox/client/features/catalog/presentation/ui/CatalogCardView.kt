package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets

@Suppress("DEPRECATION")
class CatalogCardView(context: Context, private val ui: TvWidgets, private val accent: Int) :
    FrameLayout(context) {

    private val cardRoot =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
        }

    private val artworkFrame = FrameLayout(context).apply { clipChildren = true }

    private val artworkContainer = FrameLayout(context)
    private val fallbackPanel = LinearLayout(context)

    private val durationBadge =
        TextView(context).apply {
            textSize = 10.5f
            typeface = ui.bold
            setTextColor(Color.WHITE)
            setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(Color.argb(210, 10, 15, 16))
                    cornerRadius = ui.dp(4).toFloat()
                }
            )
            setPadding(ui.dp(5), ui.dp(2), ui.dp(5), ui.dp(2))
        }

    private val favoriteBadge =
        TextView(context).apply {
            text = "★"
            textSize = 14f
            setTextColor(Color.rgb(255, 204, 0))
            setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(Color.argb(210, 10, 15, 16))
                    cornerRadius = ui.dp(4).toFloat()
                }
            )
            setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
        }

    private val progressBar = CardProgressBar(context, accent)

    private val metaLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(6), ui.dp(5), ui.dp(6), ui.dp(5))
        }

    private val titleView =
        TextView(context).apply {
            textSize = 13.5f
            typeface = ui.bold
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, ui.dp(2))
        }

    private val subtitleView =
        TextView(context).apply {
            textSize = 11.5f
            typeface = TvTypography.regular(context)
            setTextColor(ui.muted)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }

    init {
        isFocusable = false
        isClickable = false

        val normalBg =
            GradientDrawable().apply {
                setColor(Color.rgb(18, 24, 26))
                cornerRadius = ui.dp(8).toFloat()
                setStroke(ui.dp(1), Color.rgb(36, 46, 50))
            }
        val focusedBg =
            GradientDrawable().apply {
                setColor(Color.rgb(26, 34, 38))
                cornerRadius = ui.dp(8).toFloat()
                setStroke(ui.dp(2), accent)
            }
        cardRoot.setBackgroundDrawable(
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focusedBg)
                addState(intArrayOf(android.R.attr.state_pressed), focusedBg)
                addState(intArrayOf(android.R.attr.state_selected), focusedBg)
                addState(intArrayOf(), normalBg)
            }
        )

        artworkFrame.addView(artworkContainer, FrameLayout.LayoutParams(-1, -1))
        artworkFrame.addView(fallbackPanel, FrameLayout.LayoutParams(-1, -1))
        artworkFrame.addView(
            durationBadge,
            FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.BOTTOM or Gravity.RIGHT
                setMargins(0, 0, ui.dp(6), ui.dp(6))
            },
        )
        artworkFrame.addView(
            favoriteBadge,
            FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                setMargins(0, ui.dp(6), ui.dp(6), 0)
            },
        )
        artworkFrame.addView(
            progressBar,
            FrameLayout.LayoutParams(-1, ui.dp(3)).apply { gravity = Gravity.BOTTOM },
        )

        metaLayout.addView(titleView)
        metaLayout.addView(subtitleView)

        cardRoot.addView(artworkFrame)
        cardRoot.addView(metaLayout)
        addView(cardRoot, LayoutParams(-1, -1))
    }

    fun bind(
        item: MediaItem,
        cardType: CatalogCardType,
        cardWidth: Int,
        artworkProvider: (MediaItem) -> View?,
    ) {
        val artHeight =
            when (cardType) {
                CatalogCardType.PORTRAIT_POSTER -> (cardWidth * 1.48f).toInt()
                CatalogCardType.LANDSCAPE_16_9 -> (cardWidth * 9 / 16)
                CatalogCardType.CHANNEL -> ui.dp(56)
                CatalogCardType.FOLDER_FALLBACK -> ui.dp(64)
            }
        artworkFrame.layoutParams = LinearLayout.LayoutParams(-1, artHeight)

        titleView.text = item.title

        val isFolder = item.browseId.isNotEmpty()
        subtitleView.text =
            when {
                isFolder && item.subtitle.isEmpty() -> context.getString(R.string.catalog_folder)
                item.positionMs > 0 && item.durationMs <= 0 ->
                    context.getString(R.string.resume_at, ui.formatTime(item.positionMs))
                item.subtitle.isNotEmpty() -> item.subtitle
                item.category.isNotEmpty() -> item.category
                else -> ""
            }
        subtitleView.visibility = if (subtitleView.text.isEmpty()) View.GONE else View.VISIBLE

        if (item.durationMs > 0 && cardType == CatalogCardType.LANDSCAPE_16_9) {
            durationBadge.text = ui.formatTime(item.durationMs)
            durationBadge.visibility = View.VISIBLE
        } else {
            durationBadge.visibility = View.GONE
        }

        favoriteBadge.visibility = if (item.favorite) View.VISIBLE else View.GONE

        if (item.positionMs > 0 && item.durationMs > 0) {
            progressBar.setProgress(item.positionMs, item.durationMs)
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }

        artworkContainer.removeAllViews()
        if (item.imageUrl.isNotEmpty()) {
            val artView = artworkProvider(item)
            if (artView != null) {
                artworkContainer.addView(artView, FrameLayout.LayoutParams(-1, -1))
                fallbackPanel.visibility = View.GONE
                artworkContainer.visibility = View.VISIBLE
            } else {
                artworkContainer.visibility = View.GONE
                fallbackPanel.visibility = View.VISIBLE
                bindFallback(item, isFolder)
            }
        } else {
            artworkContainer.visibility = View.GONE
            fallbackPanel.visibility = View.VISIBLE
            bindFallback(item, isFolder)
        }
    }

    private fun bindFallback(item: MediaItem, isFolder: Boolean) {
        fallbackPanel.removeAllViews()
        fallbackPanel.gravity = Gravity.CENTER
        fallbackPanel.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(Color.rgb(22, 28, 30))
                cornerRadius = ui.dp(6).toFloat()
                setStroke(ui.dp(1), Color.rgb(38, 48, 52))
            }
        )
        val label =
            TextView(context).apply {
                text =
                    when {
                        isFolder -> context.getString(R.string.catalog_folder)
                        else -> ui.serviceTitle(item.provider)
                    }
                typeface = ui.bold
                textSize = 12f
                setTextColor(accent)
                gravity = Gravity.CENTER
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            }
        fallbackPanel.addView(label)
    }

    private class CardProgressBar(context: Context, private val accentColor: Int) : View(context) {
        private val paint = Paint()
        private var fraction: Float = 0f

        init {
            isFocusable = false
        }

        fun setProgress(positionMs: Int, durationMs: Int) {
            fraction = (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = Color.rgb(50, 60, 64)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = accentColor
            canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), paint)
        }
    }
}
