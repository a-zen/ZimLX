/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zimmob.zimlx.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Keep
import com.android.launcher3.R

@Keep
open class AutoModeSeekbarPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : SeekbarPreference(context, attrs, defStyleAttr) {

    protected val low: Float = min

    init {
        min -= (max - min) / steps
        steps += 1
        defaultValue = min
    }

    override fun updateSummary() {
        if (current < low) {
            mValueText!!.text = context.getString(R.string.automatic_short)
        } else {
            super.updateSummary()
        }
    }

}