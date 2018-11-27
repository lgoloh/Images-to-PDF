package swati4star.createpdf.interfaces;

import android.net.Uri;

public interface DriveServiceListener {

    boolean isLoggedOn();

    Uri fileDownloaded();

    boolean isLoggedOff();
}
