package com.dokeraj.androtainer.models

/** field to sort the container list by (Stage 1) */
enum class KontainerSortField {
    NAME,
    UPTIME,
    CREATED,
    CPU,
    MEMORY,
}

/** multi-select state filters for the container list; empty selection = show all */
enum class KontainerStateFilter {
    RUNNING,
    HEALTHY,
    EXITED,
    CREATED
}
