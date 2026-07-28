package com.mitocode.exception;

import java.time.LocalDateTime;

//record es una clase inmutable
public record CustomErrorRecord(
         LocalDateTime datetime,
         String message,
         String details
)
{
}
