package ru.cleverpumpkin.calendar

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.AttrRes
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import ru.cleverpumpkin.calendar.decorations.GridDividerItemDecoration
import ru.cleverpumpkin.calendar.decorations.ItemSelectionDecoration
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.*

class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0

) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DAY_OF_WEEK_FORMAT = "EE"
        private const val DAYS_IN_WEEK = 7
        private const val MAX_RECYCLED_DAY_VIEWS = 90
        private const val MONTHS_PER_PAGE = 6

        private const val BUNDLE_SUPER_STATE = "ru.cleverpumpkin.calendar.super_state"
        private const val BUNDLE_DISPLAY_DATE_RANGE = "ru.cleverpumpkin.calendar.display_date_range"
        private const val BUNDLE_LIMIT_DATE_RANGE = "ru.cleverpumpkin.calendar.limit_date_range"
        private const val BUNDLE_SELECTED_DATES = "ru.cleverpumpkin.calendar.selected_items"
    }

    private val daysContainer: ViewGroup
    private val recyclerView: RecyclerView

    private val calendarAdapter: CalendarAdapter
    private var calendarInitialized = false

    private lateinit var displayDatesRange: DisplayDatesRange
    private lateinit var minMaxDatesRange: MinMaxDatesRange
    private var selectedDatesHolder = SelectedDatesHolder()

    private val calendarItemsGenerator = CalendarItemsGenerator()

    init {
        Log.d("CalendarView", "setUp")
        LayoutInflater.from(context).inflate(R.layout.view_calendar, this, true)

        daysContainer = findViewById(R.id.days_container)
        recyclerView = findViewById(R.id.recycler_view)

        calendarAdapter = CalendarAdapter { date, adapterPosition ->
            Log.d("Debug: ", "Date clicked: $date")
            selectedDatesHolder.selectedDates.add(date)
            recyclerView.adapter.notifyItemChanged(adapterPosition)
        }

        setupRecyclerView(recyclerView)
        setupDaysContainer(daysContainer)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val layoutManager = GridLayoutManager(context, DAYS_IN_WEEK)

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (recyclerView.adapter.getItemViewType(position)) {
                    CalendarAdapter.MONTH_VIEW_TYPE -> DAYS_IN_WEEK
                    else -> 1
                }
            }
        }

        recyclerView.run {
            this.adapter = calendarAdapter
            this.layoutManager = layoutManager

            this.recycledViewPool.setMaxRecycledViews(
                CalendarAdapter.DAY_VIEW_TYPE,
                MAX_RECYCLED_DAY_VIEWS
            )

            this.addItemDecoration(GridDividerItemDecoration())
            this.addItemDecoration(ItemSelectionDecoration(selectedDatesHolder.selectedDates))

            val calendarScrollListener = CalendarScrollListener(
                generatePrevItems = Runnable { generatePrevMonthsItems() },
                generateNextItems = Runnable { generateNextMonthsItems() }
            )

            this.addOnScrollListener(calendarScrollListener)
        }
    }

    private fun setupDaysContainer(weekDaysContainer: ViewGroup) {
        if (weekDaysContainer.childCount != DAYS_IN_WEEK) {
            throw IllegalStateException("Days container has incorrect number of child views")
        }

        val calendar = Calendar.getInstance()
        val dayOfWeekFormatter = SimpleDateFormat(DAY_OF_WEEK_FORMAT, Locale.getDefault())
        val positionedDaysOfWeek = calendarItemsGenerator.positionedDaysOfWeek

        for (position in 0 until DAYS_IN_WEEK) {
            val dayView = weekDaysContainer.getChildAt(position) as TextView
            val dayOfWeek = positionedDaysOfWeek[position]

            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
            dayView.text = dayOfWeekFormatter.format(calendar.time)
        }
    }

    fun setUp(initialDate: Date = Date(), minDate: Date? = null, maxDate: Date? = null) {
        when {
            minDate == null && maxDate == null -> {
                val dateFrom = SimpleLocalDate(initialDate).minusMonths(MONTHS_PER_PAGE)
                val dateTo = SimpleLocalDate(initialDate).plusMonths(MONTHS_PER_PAGE)

                val calendarItems = calendarItemsGenerator.generateCalendarItems(
                    dateFrom = dateFrom.toDate(),
                    dateTo = dateTo.toDate()
                )

                calendarAdapter.setItems(calendarItems)

                displayDatesRange = DisplayDatesRange(dateFrom = dateFrom, dateTo = dateTo)
                minMaxDatesRange = MinMaxDatesRange(minDate = null, maxDate = null)
            }

            minDate != null && maxDate != null -> {
                val dateFrom = SimpleLocalDate(minDate)
                val dateTo = SimpleLocalDate(maxDate)

                val calendarItems = calendarItemsGenerator.generateCalendarItems(
                    dateFrom = dateFrom.toDate(),
                    dateTo = dateTo.toDate()
                )

                calendarAdapter.setItems(calendarItems)

                displayDatesRange = DisplayDatesRange(dateFrom = dateFrom, dateTo = dateTo)
                minMaxDatesRange = MinMaxDatesRange(minDate = dateFrom, maxDate = dateTo)
            }

            minDate != null && maxDate == null -> {
                val dateFrom = SimpleLocalDate(minDate)
                val dateTo = SimpleLocalDate(minDate).plusMonths(MONTHS_PER_PAGE)

                val calendarItems = calendarItemsGenerator.generateCalendarItems(
                    dateFrom = dateFrom.toDate(),
                    dateTo = dateTo.toDate()
                )

                calendarAdapter.setItems(calendarItems)

                displayDatesRange = DisplayDatesRange(dateFrom = dateFrom, dateTo = dateTo)
                minMaxDatesRange = MinMaxDatesRange(minDate = dateFrom, maxDate = null)
            }

            minDate == null && maxDate != null -> {
                val dateFrom = SimpleLocalDate(maxDate).minusMonths(MONTHS_PER_PAGE)
                val dateTo = SimpleLocalDate(maxDate)

                val calendarItems = calendarItemsGenerator.generateCalendarItems(
                    dateFrom = dateFrom.toDate(),
                    dateTo = dateTo.toDate()
                )

                calendarAdapter.setItems(calendarItems)

                displayDatesRange = DisplayDatesRange(dateFrom = dateFrom, dateTo = dateTo)
                minMaxDatesRange = MinMaxDatesRange(minDate = null, maxDate = dateTo)
            }
        }

        val localInitialDate = SimpleLocalDate(initialDate)
        val initialMonthPosition = calendarAdapter.findMonthItemPosition(
            localInitialDate.year,
            localInitialDate.month
        )

        if (initialMonthPosition != -1) {
            recyclerView.scrollToPosition(initialMonthPosition)
        }

        calendarInitialized = true
    }

    private fun generatePrevMonthsItems() {
        if (this.minMaxDatesRange.minDate != null) {
            return
        }

        val dateTo = displayDatesRange.dateFrom.minusMonths(1)
        val dateFrom = dateTo.minusMonths(MONTHS_PER_PAGE)

        val calendarItems = calendarItemsGenerator.generateCalendarItems(
            dateFrom = dateFrom.toDate(),
            dateTo = dateTo.toDate()
        )

        calendarAdapter.addPrevCalendarItems(calendarItems)
        displayDatesRange = displayDatesRange.copy(dateFrom = dateFrom)
    }

    private fun generateNextMonthsItems() {
        if (this.minMaxDatesRange.maxDate != null) {
            return
        }

        val fromDate = displayDatesRange.dateTo.plusMonths(1)
        val toDate = fromDate.plusMonths(MONTHS_PER_PAGE)

        val calendarItems = calendarItemsGenerator.generateCalendarItems(
            dateFrom = fromDate.toDate(),
            dateTo = toDate.toDate()
        )

        calendarAdapter.addNextCalendarItems(calendarItems)
        displayDatesRange = displayDatesRange.copy(dateTo = toDate)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()

        return Bundle().apply {
            putParcelable(BUNDLE_SUPER_STATE, superState)
            putParcelable(BUNDLE_DISPLAY_DATE_RANGE, displayDatesRange)
            putParcelable(BUNDLE_LIMIT_DATE_RANGE, minMaxDatesRange)
            putLongArray(BUNDLE_SELECTED_DATES, selectedDatesHolder.mapSelectedDatesToLongArray())
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            displayDatesRange = state.getParcelable(BUNDLE_DISPLAY_DATE_RANGE)
            minMaxDatesRange = state.getParcelable(BUNDLE_LIMIT_DATE_RANGE)

            val selectedDatesLongArray = state.getLongArray(BUNDLE_SELECTED_DATES)
            selectedDatesHolder.restoreSelectedDatesFromLongArray(selectedDatesLongArray)

            val superState: Parcelable? = state.getParcelable(BUNDLE_SUPER_STATE)
            super.onRestoreInstanceState(superState)
        } else {
            super.onRestoreInstanceState(state)
        }

        val calendarItems = calendarItemsGenerator.generateCalendarItems(
            dateFrom = displayDatesRange.dateFrom.toDate(),
            dateTo = displayDatesRange.dateTo.toDate()
        )

        calendarAdapter.setItems(calendarItems)
    }
}