package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.tools.AvailabilityTool;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.domain.Availability;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityService {
    private final AvailabilityTool availabilityTool;

    public AvailabilityService(AvailabilityTool availabilityTool) {
        this.availabilityTool = availabilityTool;
    }

    public Availability check(BookingRequestState state) {
        return availabilityTool.checkAvailability(
                state.city,
                state.roomType,
                state.checkIn.toString(),
                state.checkOut.toString()
        );
    }
}
