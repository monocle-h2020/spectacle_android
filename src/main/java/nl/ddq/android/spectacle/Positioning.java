package nl.ddq.android.spectacle;

import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.SPA;
import java.util.GregorianCalendar;

public class Positioning {


    static public AzimuthZenithAngle calcsunpos (double latit, double longit)

    {
        // calc sun pos
        final GregorianCalendar dateTime = new GregorianCalendar();

        AzimuthZenithAngle position = SPA.calculateSolarPosition(
                dateTime,
                latit, // latitude (degrees)
                longit, // longitude (degrees)
                190, // elevation (m)
                DeltaT.estimate(dateTime), // delta T (s)
                1010, // avg. air pressure (hPa)
                11); // avg. air temperature (°C)

        //  Log.i("Sunpos", position);
        return position;

    }



}
