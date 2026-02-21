package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.result.EmailSendResult;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailTool {
    private final JavaMailSender mailSender;

    public EmailTool(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Tool("Envoie un email de confirmation de réservation au client.")
    public EmailSendResult sendBookingConfirmationEmail(Booking booking) {
        try {
            if (booking == null || booking.guest().email() == null || !booking.guest().email().contains("@")) {
                return new EmailSendResult(EmailSendResult.Status.INVALID_INPUT, "Email invalide.");
            }
            String email = booking.guest().email();

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject("Confirmation de réservation " + booking.bookingRef());
            msg.setText("""
            Bonjour,
        
            Votre réservation est confirmée ✅
        
            Référence: %s
            Ville: %s
            Dates: %s → %s
            Chambre: %s
            Voyageurs: %d
            Total: %.2f EUR
        
            Merci et à bientôt.
            """.formatted(
                          booking.bookingRef(),
                          booking.city(),
                          booking.checkIn(),
                          booking.checkOut(),
                          booking.roomType(),
                          booking.guests(),
                          booking.totalPrice()
            ));
            mailSender.send(msg);
            return new EmailSendResult(EmailSendResult.Status.OK, "Email envoyé à " + email);
        }catch (Exception ex) {
            return new EmailSendResult(EmailSendResult.Status.ERROR, "Erreur email: " + ex.getMessage());
        }
    }
}
