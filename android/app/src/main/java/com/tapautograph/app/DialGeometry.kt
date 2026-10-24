package com.tapautograph.app

import kotlin.math.*

/** Six clockwise slots, beginning at twelve o'clock; center/outside cancel. */
object DialGeometry {
    fun slot(x:Float,y:Float,deadZone:Float=28f,cancelRadius:Float=210f):Int? {
        if(!x.isFinite() || !y.isFinite())return null
        val distance=hypot(x,y)
        if(distance<deadZone || distance>cancelRadius)return null
        val degrees=(Math.toDegrees(atan2(y.toDouble(),x.toDouble()))+90+360)%360
        return (floor((degrees+30)/60).toInt()%6)
    }
}
