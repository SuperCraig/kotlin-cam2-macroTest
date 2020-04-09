package com.example.imagegallery.adapter

data class Image (
    val imageUrl: String,
    val title: String,
    var isSelected: Boolean
)