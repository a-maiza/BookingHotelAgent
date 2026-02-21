package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.tools.EmailTool;
import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.result.EmailSendResult;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final EmailTool emailTool;

    public EmailService(EmailTool emailTool) {
        this.emailTool = emailTool;
    }

    public EmailSendResult sendBookingConfirmation(Booking booking) {
        return emailTool.sendBookingConfirmationEmail(booking);
    }
}
