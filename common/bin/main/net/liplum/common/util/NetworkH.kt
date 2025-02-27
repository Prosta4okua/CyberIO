package net.liplum.common.util

import arc.util.Http.HttpRequest

fun HttpRequest.useFakeHeader(): HttpRequest {
    return this.header(
        "User-Agent",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36"
    )
}