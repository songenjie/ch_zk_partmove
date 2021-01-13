package metadata;

import org.apache.log4j.Logger;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Timer {
    private static Logger LOGGER = Logger.getLogger(Timer.class);

    public static  String GetLasteDayTime() {
        LOGGER.debug("GetLasteDayTime ");

        Date d = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LOGGER.debug("GetLasteDayTime :"+ df.format(new Date(d.getTime() - (long) 1 * 24 * 60 * 60 * 1000)));
        return df.format(new Date(d.getTime() - (long) 1 * 24 * 60 * 60 * 1000));
    }

    public static  String GetOldDayTime() {
        LOGGER.info("GetOldDayTime ");
        Date d = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LOGGER.info("GetOldDayTime :"+ df.format(new Date(d.getTime() - (long) 100 * 24 * 60 * 60 * 1000)));
        return df.format(new Date(d.getTime() - (long) 100 * 24 * 60 * 60 * 1000));
    }

    public  static String GetNowDateTime() {
        LOGGER.info("GetNowDateTime ");
        Date d = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LOGGER.info("GetNowDateTime "+ df.format(new Date(d.getTime())));
        return df.format(new Date(d.getTime()));

    }

    public static  String ConverLongToTIme(long time){
        LOGGER.debug("ConverLongToTIme "+ time);
        Date date = new Date(time);
        Format format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LOGGER.debug("ConverLongToTIme "+ format.format(date) + " done ! ");
        return format.format(date);
    }
}
