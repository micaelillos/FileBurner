package xmodemburner;

public class Xmodem {
    /* Xmodem values */
    public static final byte SOH = 0x01; /* Start Of Header */
    public static final byte EOT = 0x18; /* End Of Transmission */
    public static final byte ACK = 0x06; /* ACKnowlege */
    public static final byte NAK = 0x15; /* Negative AcKnowlege */
    public static final byte CAN = 0x18; /* CANcel character */
    public static final byte C = 0x43; /* Asci C */
    public static final int RETRIES    = 10; /* AMount of retrys */
    // Transfer data in 128-byte blocks
    public static final int sector_size=128;
    public static final int packet_size=133;







}
