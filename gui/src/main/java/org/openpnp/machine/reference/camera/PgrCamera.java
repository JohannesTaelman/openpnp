package org.openpnp.machine.reference.camera;

import com.pointgrey.api.PGCameraMode;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.openpnp.ConfigurationListener;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceCamera;
import org.openpnp.model.Configuration;
import org.simpleframework.xml.Attribute;

import static com.pointgrey.api.PointGreyCameraInterface.connectToDefaultCamera;
import static com.pointgrey.api.PointGreyCameraInterface.createContext;
import static com.pointgrey.api.PointGreyCameraInterface.getCameraName;
import static com.pointgrey.api.PointGreyCameraInterface.getNumOfCameras;
import static com.pointgrey.api.PointGreyCameraInterface.getSupportedCameraModes;
import static com.pointgrey.api.PointGreyCameraInterface.startCapture;
import static com.pointgrey.api.PointGreyCameraInterface.setCameraMode;
import static com.pointgrey.api.PointGreyCameraInterface.storeImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import org.openpnp.machine.reference.camera.wizards.PgrCameraConfigurationWizard;

/**
 *
 * @author jtaelman
 */

public class PgrCamera extends ReferenceCamera implements Runnable {
    private BufferedImage lastImage;
    private Object captureLock = new Object();
    private Thread captureThread;
    @Attribute(required=false)
    private String driver;

    public PgrCamera() {
    Configuration.get().addListener(new ConfigurationListener.Adapter() {

        @Override
        public void configurationLoaded(Configuration configuration)
                throws Exception {
            if (driver != null && driver.trim().length() != 0) {
                setDriver(driver);
            }
        }
    });
    }

    public String getDriver() {
            return driver;
    }

    public void setDriver(String driver) {
            if (captureThread != null) {
                    captureThread.interrupt();
                    try {
                            captureThread.join();
                    }
                    catch (Exception e) {
                            e.printStackTrace();
                    }
            }
            this.driver = driver;
            captureThread = new Thread(this);
            captureThread.start();
    }
    
    
    @Override
    public BufferedImage capture() {
            synchronized (captureLock) {
                    try {
                            captureLock.wait();
                            BufferedImage image = lastImage;
                            return image;
                    }
                    catch (Exception e) {
                            e.printStackTrace();
                            return null;
                    }
            }
    }

    @Override
    public Wizard getConfigurationWizard() {
            return new PgrCameraConfigurationWizard(this);
    }

    @Override
    public void run() {
        System.out.println("Creating a context for the camera.");
        createContext();
        
        System.out.println("\nThere are " + getNumOfCameras() + " camera(s) connected to the system");
        System.out.println("Connecting to the default camera.");
        connectToDefaultCamera();
        System.out.println("Connected to: " + getCameraName());
        
        System.out.println("\nStarting capture.");
        startCapture();

        PGCameraMode mode = getSupportedCameraModes()[2]; // 30 fps
        System.out.println("Capturing an image with mode: " + mode );

        //Changing the camera mode will cause the camera to stop capturing, it must be started again.
        setCameraMode(mode);
        startCapture();

        int width = mode.getVideoMode().getWidth();
        int height = mode.getVideoMode().getHeight();
        while (!Thread.interrupted()) {
            byte[] buff = new byte[width*height*3];
            storeImage(buff);
            // assuming Y8 !
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = img.getRaster();
//            WritableRaster raster = Raster.createPackedRaster(DataBuffer.TYPE_BYTE, width, height, 1, 8, null);
            raster.setDataElements(0, 0, width, height, buff);
//raster.setPixels(0, 0, width, height, buff);
            img.setData(raster);
//            DataBufferByte db = (DataBufferByte)img.getRaster().getDataBuffer();
//            System.out.println("db size : "+ db.getSize());
            broadcastCapture(lastImage = img);
            synchronized (captureLock) {
                    captureLock.notify();
            }
            try {
                    Thread.sleep(1000 / 30);
            }
            catch (Exception e) {
            }
        }            
    }
    
}
