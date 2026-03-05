package com.fupfin.midiraja.midi;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A simple utility to dump raw PCM shorts into a valid WAV file.
 */
public class WavFileWriter implements AutoCloseable
{
    private final FileOutputStream out;
    private int dataSize = 0;
    private final int sampleRate;
    private final int channels;

    public WavFileWriter(String filename, int sampleRate, int channels) throws IOException
    {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.out = new FileOutputStream(filename);
        writeHeader(); // Write placeholder header
    }

    public void write(short[] buffer) throws IOException
    {
        byte[] bytes = new byte[buffer.length * 2];
        for (int i = 0; i < buffer.length; i++)
        {
            short s = buffer[i];
            bytes[i * 2] = (byte) (s & 0xff);
            bytes[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        out.write(bytes);
        dataSize += bytes.length;
    }

    private void writeHeader() throws IOException
    {
        int byteRate = sampleRate * channels * 2;
        int blockAlign = channels * 2;
        int totalDataLen = dataSize + 36;

        byte[] header = new byte[44];
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0; // Subchunk1Size
        header[20] = 1;
        header[21] = 0; // AudioFormat (1 = PCM)
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign;
        header[33] = 0;
        header[34] = 16;
        header[35] = 0; // BitsPerSample
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (dataSize & 0xff);
        header[41] = (byte) ((dataSize >> 8) & 0xff);
        header[42] = (byte) ((dataSize >> 16) & 0xff);
        header[43] = (byte) ((dataSize >> 24) & 0xff);

        out.write(header);
    }

    @Override
    public void close() throws IOException
    {
        // Go back to the beginning to patch the sizes in the header
        out.getChannel().position(0);
        writeHeader();
        out.close();
    }
}
