package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.DataFileOperatorCreateFailedException;
import com.a.eye.skywalking.storage.data.exception.SpanDataStoredFailedException;
import com.a.eye.skywalking.storage.data.exception.SpanDataReadFailedException;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.a.eye.skywalking.storage.util.PathResolver.getAbsolutePath;

/**
 * 数据文件
 */
public class DataFile {

    private static ILog logger = LogManager.getLogger(DataFile.class);
    private DataFileNameDesc           nameDesc;
    private long             currentOffset;
    private DataFileOperator operator;


    static {
        File dataFileDir = new File(getAbsolutePath(Config.DataFile.PATH));
        if (!dataFileDir.exists()) {
            dataFileDir.mkdirs();
        }
    }

    public DataFile() {
        this.nameDesc = new DataFileNameDesc();
        this.currentOffset = 0;
        operator = new DataFileOperator();
        createFile();
    }

    public DataFile(String fileName){
        this.nameDesc = new DataFileNameDesc(fileName);
        this.currentOffset = 0;
        operator = new DataFileOperator();
        createFile();
    }

    public DataFile(DataFileNameDesc nameDesc) {
        this.nameDesc = nameDesc;
        operator = new DataFileOperator();
        createFile();
    }

    public DataFile(File file) {
        this.nameDesc = new DataFileNameDesc(file.getName());
        this.currentOffset = file.length();
        operator = new DataFileOperator();
        createFile();
    }

    private void createFile() {
        File dataFile = getDataFile();
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                if (logger.isDebugEnable()) {
                    logger.debug("Create an new data file[{}].", nameDesc.fileName());
                }
                HealthCollector.getCurrentHeathReading("DataFile")
                        .updateData(HeathReading.INFO, "Create an new data " + "file.");
            } catch (IOException e) {
                logger.error("Failed to create data file.", e);
                throw new DataFileOperatorCreateFailedException("Failed to create data file", e);
            }
        }
    }

    public boolean overLimitLength() {
        boolean isOverLimitLength = currentOffset >= Config.DataFile.SIZE;
        if (isOverLimitLength) {
            logger.info("Data File[{}] is over limit length.", nameDesc.fileName());
        }
        return isOverLimitLength;
    }

    public IndexMetaInfo write(SpanData data) {
        byte[] bytes = data.toByteArray();
        try {
            operator.getWriter().write(bytes);
            IndexMetaInfo metaInfo = new IndexMetaInfo(data, nameDesc, currentOffset, bytes.length);
            currentOffset += bytes.length;
            return metaInfo;
        } catch (IOException e) {
            throw new SpanDataStoredFailedException(e);
        }
    }

    public void flush() {
        try {
            operator.getWriter().flush();
        } catch (IOException e) {
            throw new SpanDataStoredFailedException(e);
        }
    }

    public void close() {
        operator.close();
    }

    public byte[] read(long offset, int length) {
        byte[] data = new byte[length];
        try {
            operator.getReader().getChannel().position(offset);
            operator.getReader().read(data, 0, length);
            return data;
        } catch (IOException e) {
            throw new SpanDataReadFailedException(
                    "Failed to read dataFile[" + nameDesc.fileName() + "], offset: " + offset + " " + "lenght: " + length, e);
        }
    }

    class DataFileOperator {
        private FileOutputStream writer;
        private FileInputStream  reader;

        public FileOutputStream getWriter() {

            if (writer == null) {
                try {
                    writer = new FileOutputStream(getDataFile(), true);
                } catch (IOException e) {
                    throw new DataFileOperatorCreateFailedException("Failed to create datafile output stream", e);
                }
            }

            return writer;
        }

        public FileInputStream getReader() {
            if (reader == null) {
                try {
                    reader = new FileInputStream(getDataFile());
                } catch (IOException e) {
                    throw new DataFileOperatorCreateFailedException("Failed to create datafile input stream", e);
                }
            }

            return reader;
        }

        public void close() {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {

                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {

                }
            }
        }
    }


    private File getDataFile() {
        return new File(getAbsolutePath(Config.DataFile.PATH), nameDesc.fileName());
    }

    @Override
    public String toString() {
        return "DataFile{" + "fileName='" + nameDesc.fileName() + '\'' + '}';
    }
}
