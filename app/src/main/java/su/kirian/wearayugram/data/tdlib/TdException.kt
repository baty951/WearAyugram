package su.kirian.wearayugram.data.tdlib

class TdException(val code: Int, message: String) : Exception("TDLib error $code: $message")
