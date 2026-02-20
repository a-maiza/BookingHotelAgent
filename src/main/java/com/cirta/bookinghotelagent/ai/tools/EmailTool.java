package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Booking;
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
    public String sendBookingConfirmationEmail(Booking booking) {
        String to = booking.guest().email();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
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
        return "Email envoyé à " + booking.guest();
    }
}
