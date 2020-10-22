package xmodemburner;

import java.nio.ByteBuffer;

public class CRC16 {
    public final static int polynomial = 0x1021;	// Represents x^16+x^12+x^5+1
    int crc;

    public CRC16(){
        crc = 0x0000;
    }

    public int getCRC(){
        return crc;
    }

    public byte[] getCRCBytes(){
        return ByteBuffer.allocate(4).putInt(crc).array();
    }

    public String getCRCHexString(){
        String crcHexString = Integer.toHexString(crc);
        return crcHexString;
    }

    public void resetCRC(){
        crc = 0xFFFF;
    }

    public void update(byte[] args) {
        for (byte b : args) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b   >> (7-i) & 1) == 1);
                boolean c15 = ((crc >> 15    & 1) == 1);
                crc <<= 1;
                // If coefficient of bit and remainder polynomial = 1 xor crc with polynomial
                if (c15 ^ bit) crc ^= polynomial;
            }
        }

        crc &= 0xffff;
    }
}