package com.vloginova.midx.util

import com.vloginova.midx.util.collections.IntSet

fun IntSet.toSet() : Set<Int>  {
    val resultSet = HashSet<Int>()
    for (value in this) {
        resultSet.add(value)
    }
    return resultSet
}