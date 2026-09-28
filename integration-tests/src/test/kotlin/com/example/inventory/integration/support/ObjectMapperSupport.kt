package com.example.inventory.integration.support

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

/** Plain Jackson mapper matching the configuration production services rely on for wire types. */
fun integrationObjectMapper(): ObjectMapper = jacksonMapperBuilder().build()
