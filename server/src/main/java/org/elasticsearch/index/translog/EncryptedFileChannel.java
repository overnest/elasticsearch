package org.elasticsearch.index.translog;

import org.apache.lucene.util.crypto.Crypto;
import org.apache.lucene.util.crypto.CtrCipher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class EncryptedFileChannel extends FileChannel {
    final private int MAX_TRANSFER_BUFFER_SIZE = 8192;
    private CtrCipher cipher;
    private FileChannel channel;

    public static FileChannel open(Path path, OpenOption... options) throws IOException {
        return new EncryptedFileChannel(path, options);
    }

    private EncryptedFileChannel(Path path, OpenOption... options) throws IOException {
        try {
            this.cipher = Crypto.getCtrCipher(Crypto.getAesKey(), Crypto.getAesIV());
            this.channel = FileChannel.open(path, options);
        }catch(FileNotFoundException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ByteBuffer tmp = ByteBuffer.allocate(dst.limit() - dst.position());
        long position = this.channel.position();
        int read = this.channel.read(tmp);
        if (read <= 0) {
            return read;
        }
        tmp.flip();
        dst.put(this.cipher.decrypt(tmp, position), 0, read);

        return read;
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        ByteBuffer tmp = ByteBuffer.allocate(dst.limit() - dst.position());
        int read = this.channel.read(tmp, position);
        if (read <= 0) {
            return read;
        }
        tmp.flip();
        dst.put(this.cipher.decrypt(tmp, position), 0, read);

        return read;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        byte[] tmp = this.cipher.encrypt(src, this.channel.position());
        return this.channel.write(ByteBuffer.wrap(tmp));
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        byte[] tmp = this.cipher.encrypt(src, position);
        return this.channel.write(ByteBuffer.wrap(tmp), position);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long position() throws IOException {
        return this.channel.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        this.channel.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return this.channel.size();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        this.channel.truncate(size);
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        this.channel.force(metaData);
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        long tmpPosition = position;
        long tmpCount = count;
        long transferCount = 0;
        ByteBuffer bb = ByteBuffer.allocate(MAX_TRANSFER_BUFFER_SIZE);

        while (tmpCount > 0){
            int read = this.read(bb, tmpPosition);
            if (read <= 0) {
                break;
            }

            int dataSize = tmpCount < read ? (int) tmpCount : read;
            tmpCount -= dataSize;

            bb.flip();
            byte[] data = new byte[dataSize];
            bb.get(data, 0, dataSize);
            bb.flip();

            int write = target.write(ByteBuffer.wrap(data));
            tmpPosition += write;
            transferCount += write;
        }

        return transferCount;
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        long tmpPosition = position;
        long tmpCount = count;
        long transferCount = 0;
        ByteBuffer bb = ByteBuffer.allocate(MAX_TRANSFER_BUFFER_SIZE);

        while (tmpCount > 0){
            if(tmpCount < MAX_TRANSFER_BUFFER_SIZE){
                bb.limit((int) tmpCount);
            }

            int read = src.read(bb);
            if (read <= 0) {
                break;
            }

            int dataSize = tmpCount < read ? (int) tmpCount : read;
            tmpCount -= dataSize;

            bb.flip();
            byte[] data = new byte[dataSize];
            bb.get(data, 0, dataSize);
            bb.flip();

            int write = this.write(ByteBuffer.wrap(data), tmpPosition);
            tmpPosition += write;
            transferCount += write;
        }

        return transferCount;
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return this.channel.lock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return this.channel.tryLock(position, size, shared);
    }

    @Override
    protected void implCloseChannel() throws IOException {
        this.channel.close();
    }
}
