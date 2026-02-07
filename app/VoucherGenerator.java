import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class VoucherGenerator {

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Verwijderd verwarrende letters/cijfers
    private static final int CODE_LENGTH = 10; // Bijv. "A5B7-C9D3-E1"
    private static final SecureRandom random = new SecureRandom();

    // Genereer een willekeurige vouchercode
    public static String generateVoucherCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) code.append("-"); // Format: XXXX-XXXX-XX
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    // Maak een voucher document voor Firebase
    public static Map<String, Object> createVoucherDocument(double amount) {
        Map<String, Object> voucher = new HashMap<>();
        voucher.put("code", generateVoucherCode());
        voucher.put("amount", amount);
        voucher.put("used", false);
        voucher.put("createdAt", System.currentTimeMillis());
        return voucher;
    }
}