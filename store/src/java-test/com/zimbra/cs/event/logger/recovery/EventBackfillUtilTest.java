package com.zimbra.cs.event.logger.recovery;

import com.zimbra.cs.event.recovery.EventBackfillUtil;
import com.zimbra.cs.mailbox.MailboxTestUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class EventBackfillUtilTest {

    @Test
    public void testProc() throws Exception {
        EventBackfillUtil.main(null);
    }

    @Test
    public void testDate() {
        /*DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US);
        df.setTimeZone(TimeZone.getDefault());
        df.setLenient(false);
        LocalDate date = LocalDate.now();
        LocalDate lastYear = date.minusYears(1);
        String localizedDate = df.format(lastYear.toString());
        System.out.println(localizedDate);*/
        String now = LocalDate.now().minusDays(3).minusMonths(5).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.US));
        System.out.println(now);
        String now1 = LocalDate.now().minusDays(3).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.FRENCH));
        System.out.println(now1);
    }
}
