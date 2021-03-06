package ru.cleverpumpkin.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.v4.widget.ImageViewCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View.OnClickListener
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextSwitcher
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

/**
 * This internal view class represents a year selection control.
 * It is used as a part of the Calendar Widget.
 */
internal class YearSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0

) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val YEAR_FORMAT = "yyyy"
    }

    private val arrowPrevView: ImageView
    private val arrowNextView: ImageView
    private val textSwitcher: TextSwitcher

    private val slideInBottomAnim = AnimationUtils.loadAnimation(context, R.anim.slide_in_bottom)
    private val slideInTopAnim = AnimationUtils.loadAnimation(context, R.anim.slide_in_top)
    private val slideOutBottomAnim = AnimationUtils.loadAnimation(context, R.anim.slide_out_bottom)
    private val slideOutTopAnim = AnimationUtils.loadAnimation(context, R.anim.slide_out_top)

    private val yearFormatter = SimpleDateFormat(YEAR_FORMAT, Locale.getDefault())
    private var minMaxDatesRange = NullableDatesRange()

    var displayedDate: CalendarDate = CalendarDate.today
        set(newDate) {
            val oldDate = field
            field = newDate

            if (oldDate.year != newDate.year) {
                updateAnimations(oldYear = oldDate.year, newYear = newDate.year)
                textSwitcher.setText(yearFormatter.format(newDate.date))
                updateArrowButtonsState()
            }
        }

    var onYearChangeListener: ((CalendarDate) -> Unit)? = null

    var onYearClickListener: ((Int) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_year_selection, this, true)

        arrowPrevView = findViewById(R.id.arrow_prev)
        arrowNextView = findViewById(R.id.arrow_next)
        textSwitcher = findViewById(R.id.text_switcher)

        textSwitcher.setText(yearFormatter.format(displayedDate.date))
        textSwitcher.setOnClickListener {
            onYearClickListener?.invoke(displayedDate.year)
        }

        val arrowClickListener = OnClickListener { v ->
            val (minDate, maxDate) = minMaxDatesRange
            val prevDisplayedDate = displayedDate

            displayedDate = if (v.id == R.id.arrow_prev) {
                val prevYear = displayedDate.minusMonths(CalendarDate.MONTHS_IN_YEAR)
                if (minDate == null || minDate <= prevYear) {
                    prevYear
                } else {
                    minDate
                }

            } else {
                val nextYear = displayedDate.plusMonths(CalendarDate.MONTHS_IN_YEAR)
                if (maxDate == null || maxDate >= nextYear) {
                    nextYear
                } else {
                    maxDate
                }
            }

            if (prevDisplayedDate.year != displayedDate.year) {
                onYearChangeListener?.invoke(displayedDate)
            }
        }

        arrowPrevView.setOnClickListener(arrowClickListener)
        arrowNextView.setOnClickListener(arrowClickListener)
    }

    fun setupYearSelectionView(displayedDate: CalendarDate, minMaxDatesRange: NullableDatesRange) {
        this.minMaxDatesRange = minMaxDatesRange
        this.displayedDate = displayedDate
        updateArrowButtonsState()
    }

    private fun updateAnimations(oldYear: Int, newYear: Int) {
        if (newYear > oldYear) {
            textSwitcher.outAnimation = slideOutTopAnim
            textSwitcher.inAnimation = slideInBottomAnim
        } else {
            textSwitcher.outAnimation = slideOutBottomAnim
            textSwitcher.inAnimation = slideInTopAnim
        }
    }

    private fun updateArrowButtonsState() {
        val (minDate, maxDate) = minMaxDatesRange

        if (minDate == null || minDate.year <= displayedDate.year.dec()) {
            arrowPrevView.isClickable = true
            arrowPrevView.alpha = 1.0f
        } else {
            arrowPrevView.isClickable = false
            arrowPrevView.alpha = 0.2f
        }

        if (maxDate == null || maxDate.year >= displayedDate.year.inc()) {
            arrowNextView.isClickable = true
            arrowNextView.alpha = 1.0f
        } else {
            arrowNextView.isClickable = false
            arrowNextView.alpha = 0.2f
        }
    }

    fun applyStyle(style: YearSelectionStyle) {
        setBackgroundColor(style.background)
        ImageViewCompat.setImageTintList(arrowPrevView, ColorStateList.valueOf(style.arrowsColor))
        ImageViewCompat.setImageTintList(arrowNextView, ColorStateList.valueOf(style.arrowsColor))

        for (i in 0..textSwitcher.childCount) {
            val textView = textSwitcher.getChildAt(i) as? TextView
            textView?.setTextColor(style.yearTextColor)
        }
    }

    class YearSelectionStyle(
        @ColorInt val background: Int,
        @ColorInt val arrowsColor: Int,
        @ColorInt val yearTextColor: Int
    )
}