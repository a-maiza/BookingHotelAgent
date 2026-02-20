package com.cirta.bookinghotelagent.domain;

import java.util.Set;

public record Hotel(
        String id,
        String name,
        String city,
        Set<RoomType> supportedRoomTypes
) {
}
