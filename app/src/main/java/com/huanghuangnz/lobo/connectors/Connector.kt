package com.huanghuangnz.lobo.connectors

interface DeviceConnector{

    fun init()

    fun turn(on: Boolean)

    fun toggle()

}