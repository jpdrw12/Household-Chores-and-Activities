package com.jpdrw.household.data

import androidx.room.TypeConverter
import com.jpdrw.household.data.entity.ActivityCategory
import com.jpdrw.household.data.entity.ActivitySlot
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalAudience

class Converters {
    @TypeConverter
    fun frequencyToString(value: Frequency): String = value.name
    @TypeConverter
    fun stringToFrequency(value: String): Frequency = Frequency.valueOf(value)

    @TypeConverter
    fun categoryToString(value: ActivityCategory): String = value.name
    @TypeConverter
    fun stringToCategory(value: String): ActivityCategory = ActivityCategory.valueOf(value)

    @TypeConverter
    fun slotToString(value: ActivitySlot): String = value.name
    @TypeConverter
    fun stringToSlot(value: String): ActivitySlot = ActivitySlot.valueOf(value)

    @TypeConverter
    fun audienceToString(value: ParentalAudience): String = value.name
    @TypeConverter
    fun stringToAudience(value: String): ParentalAudience = ParentalAudience.valueOf(value)

    @TypeConverter
    fun budgetToString(value: BudgetTier): String = value.name
    @TypeConverter
    fun stringToBudget(value: String): BudgetTier = BudgetTier.valueOf(value)
}
