package com.porttunnel.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alias: String,
    val sshHost: String,
    val sshPort: Int = 22,
    val sshUsername: String,
    val remotePort: Int,
    val localPort: Int
)
