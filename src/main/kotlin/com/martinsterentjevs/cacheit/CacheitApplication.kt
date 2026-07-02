package com.martinsterentjevs.cacheit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CacheitApplication

fun main(args: Array<String>) {
	runApplication<CacheitApplication>(*args)
}
