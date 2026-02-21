package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.ai.tools.PricingTool;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
    private final PricingTool pricingTool;

    public PricingService(PricingTool pricingTool) {
        this.pricingTool = pricingTool;
    }

    public PricingResult quote(BookingRequestState state) {
        double budget = (state.budgetPerNight != null) ? state.budgetPerNight : 0.0;
        return pricingTool.quote(
                state.city,
                state.roomType,
                state.guests,
                state.checkIn.toString(),
                state.checkOut.toString(),
                budget
        );
    }
}
