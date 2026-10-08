package com.codex.shuatitimer.data.model

/**
 * Predefined study subjects/categories for Chinese civil service exam preparation.
 * 资料分析, 言语理解, 判断推理, 数量关系, 常识判断, 申论, 自定义
 */
enum class Subject(val displayName: String) {
    ZILIAO_FENXI("资料分析"),
    YANYU_LIJIE("言语理解"),
    PANDUAN_TUILI("判断推理"),
    SHULIANG_GUANXI("数量关系"),
    CHANGSHI_PANDUAN("常识判断"),
    SHENLUN("申论"),
    CUSTOM("自定义");

    companion object {
        fun fromDisplayName(name: String): Subject {
            return entries.find { it.displayName == name } ?: CUSTOM
        }

        fun allDisplayNames(): List<String> = entries.map { it.displayName }
    }
}
