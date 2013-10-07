

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Maslennikov S.
 */
import ij.*;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;
import ij.plugin.filter.MaximumFinder;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImageProcessing_ implements PlugIn{

    @Override
    public void run(String string) {
        
        if(WindowManager.getWindowCount() == 0){
            IJ.error("Please, open image!");
            return;
        }
        
        GenericDialog gd = new GenericDialog("Image Processing..."); 
        gd.addMessage("Change coefficients for processing image.");      
        gd.addMessage("---------------------------------------------------------------------");      
        gd.addNumericField("Object size: ",15,2);
        gd.addNumericField("Object threshold: ",20,2);
        gd.addNumericField("TH: ",10,2);
        gd.showDialog();
        
        if(gd.wasCanceled()){
            IJ.error("Plugin canceled");
            return;
        }
        
        //Задаем размер объектов
        int objectSize = (int)gd.getNextNumber();
        //Отступ адитивного порога
        int var = (int)gd.getNextNumber();
        
        int TH = (int)gd.getNextNumber();

        //Получаем изображение типа ImagePlus
        ImagePlus imp = WindowManager.getCurrentImage();
        
        //Конвертируем изображение в градации серого
        new ImageConverter(imp).convertToGray8();
        
        //Получаем ImageProcessor от ImagePlus
        ImageProcessor IP = imp.getProcessor();
        
        //Создаем дубликат процессора
        ImageProcessor dublIP = IP.duplicate(); 
        ImageProcessor originalIP = IP.duplicate();
        
        //Средний вариационный размах
        //int calcvar = getAvgOnSquare(IP, objectSize); 
        //IJ.log("Object threshold calculate = "+String.valueOf(calcvar));
            /********/
                
            /********/
            
            
            IJ.log("Detected size = "+String.valueOf(calculateObjectSize(IP)));
            objectSize = calculateObjectSize(IP);
            IJ.log("Object size = "+String.valueOf(objectSize));
            //var = (int)(1.3)*getAvgOnSquare(IP, objectSize);
            //var = getAvgOnSquare(IP, 2*objectSize);
            //IJ.log("Var size = "+String.valueOf(var));
            var = 2*objectSize;
            IJ.log("Object threshold = "+String.valueOf(var));
            
        
        //Определение минимума на площади AxA и добавления порога Т.
        dublIP = VariationalRange(IP, 
                          objectSize,  //A
                                 var); //T
        
        //Вычитание изображений
        IP = SubstructImages(IP,dublIP);

        //Бинаризация
        IP = makeBinaryIp(IP,var);
        
        //Нахождение пикселей объектов
        //**********************************************************************
        
        int width = IP.getWidth();
        int height = IP.getHeight();
        
        ImageProcessor tempIP = IP.duplicate();
        //Эррозия, т.к. бин. изобр. инвертировано
        IP.dilate();
        //Дилатация соответственно
        IP.erode();
        
        ImageProcessor dip = IP.duplicate();
        ArrayList<ArrayList<Point>> AllObjects = new ArrayList<>();
        ArrayList<Point> AB = new ArrayList<>();
        ArrayList<Point> SQ = new ArrayList<>();
        ArrayList<Point> CenterObjects = new ArrayList<>();
        double S;
        int D;
        int cooX; int cooY;
        int countObjects = 0;
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                if(IP.getPixel(x, y)==255){
                    AB = getArrayObject(IP, x, y);
                    SQ = getSquereObject(AB);
                        AllObjects.add(SQ);
                    for (int i = 0; i < SQ.size(); i++){
                        IP.putPixel(SQ.get(i).x, SQ.get(i).y, 0);
                    }
                    S = (double)SQ.size();
                    D = (int)Math.sqrt((S*4)/Math.PI);
                    cooX = (int)x+D; //Dmn  /4
                    cooY = (int)y+D; //D/4
                    CenterObjects.add(new Point(cooX,cooY));
                    for (int i = 0; i < AB.size(); i++){
                        dip.putPixel(AB.get(i).x, AB.get(i).y, 150);
                        //originalIP.putPixel(AB.get(i).x, AB.get(i).y, 50);
                    }
                    
                    //ip2.drawOval(x-(int)D/2, y-(int)D/2, D, D);
                    countObjects += 1;
                    //IJ.log("Object #"+countObjects+": S="+String.valueOf(S)+" px   D="+String.valueOf(D)+" px"+"    n Coordinates: Х="+cooX+" Y="+cooY);
                }
            }
        }
        
        IP = dip.duplicate();
        //**********************************************************************
        
        
        //Поиск максимумов для найденных объектов
        CenterObjects = getMaximums2(AllObjects, originalIP);
        IJ.log("Объектов найдено: "+String.valueOf(CenterObjects.size()));

        
        
        /****/
        
        
        //Вычисляем площадь по центру объекта
        //imp.setProcessor(drawObjects(originalIP, CenterObjects)); //IP
            
            
        //Присваиваем ImagePlus ImageProcessor
        imp.setProcessor(originalIP);


        /*
        int[] xpoints = new int[CenterObjects.size()]; 
        int[] ypoints = new int[CenterObjects.size()];
        for(int i = 0; i < CenterObjects.size(); i++){
            xpoints[i] = CenterObjects.get(i).x;
            ypoints[i] = CenterObjects.get(i).y;
        }
        Roi roi = new PointRoi(xpoints, ypoints, CenterObjects.size());
        roi.setStrokeColor(Color.green); 
        Overlay overlay = new Overlay(roi); 
        imp.setOverlay(overlay);     */

        //********Detect size********
            //Создаем точки смещения
            ArrayList<Point> vectorList = new ArrayList<>();
                vectorList.add(new Point(-1,-1));
                vectorList.add(new Point(0,-1));
                vectorList.add(new Point(1,-1));
                vectorList.add(new Point(1,1));
                vectorList.add(new Point(-1,0));
                vectorList.add(new Point(-1,1));
                vectorList.add(new Point(0,1));
                vectorList.add(new Point(1,0));

            //Массив площадей
            int findArray[][] = new int[originalIP.getWidth()][originalIP.getHeight()];
            int findPoints[][] = new int[originalIP.getWidth()][originalIP.getHeight()];
            //Вписываем центры
            for(int i = 0; i < CenterObjects.size(); i++){
                int _x = CenterObjects.get(i).x;
                int _y = CenterObjects.get(i).y;
                findArray[_x][_y] = i+1;
            }

            //for(int iteration = 0; iteration <= 15; iteration++)
            boolean entropia;
            do{
                entropia = false;
                //Проход маски
                for(int i = 1; i <= CenterObjects.size(); i++){
                    for(int x = 0; x < findArray.length; x++){
                        for(int y = 0; y < findArray[0].length; y++){
                            if(findArray[x][y] == i){
                                for(int k = 0; k <= 7; k++){
                                    if(IP.getPixel(x+vectorList.get(k).x,y+vectorList.get(k).y) > 0){
                                        findPoints[x+vectorList.get(k).x][y+vectorList.get(k).y] = i;
                                        entropia = true;
                                    }
                                }
                            }
                        }
                    }
                }
                for(int x = 0; x < findArray.length; x++){
                    for(int y = 0; y < findArray[0].length; y++){
                        int i = findArray[x][y];
                        int j = findPoints[x][y];
                        if((j !=0) && (i == 0)){
                            findArray[x][y] = j;
                        }
                    }
                }
            }while(entropia);
            imp.setProcessor(IP);

            ArrayToTextImage(findArray,"findArray.txt");
            /*
            IJ.log("-------------");
            IJ.log("Intensiv: "+originalIP.getPixel(CenterObjects.get(0).x,CenterObjects.get(0).y));

            int temp[][] = new int[originalIP.getWidth()][originalIP.getHeight()];

            for (int i = 0; i < originalIP.getWidth(); i++){
                for (int j = 0; j < originalIP.getHeight(); j++){
                    temp[i][j] = 0;
                }
            }

            for (int i = 0; i < CenterObjects.size(); i++){
                int maxinten =  originalIP.getPixel(CenterObjects.get(i).x,CenterObjects.get(i).y) - TH;
                for(int x = CenterObjects.get(i).x-objectSize; x <= CenterObjects.get(i).x+objectSize;x++){
                    for (int y = CenterObjects.get(i).y-objectSize; y <= CenterObjects.get(i).y+objectSize;y++){
                        //if(originalIP.getPixel(CenterObjects.get(i).x,CenterObjects.get(i).y) >= originalIP.getPixel(CenterObjects.get(i).x,CenterObjects.get(i).y)-TH) temp[x][y] = originalIP.getPixel(CenterObjects.get(i).x,CenterObjects.get(i).y);
                        int pixel = originalIP.getPixel(x,y);
                        if(pixel >= maxinten){
                            temp[x][y] = pixel;
                        } else {
                            temp[x][y] = 0;
                        }
                    }
                }
            }

            originalIP.setIntArray(temp);
            imp.setProcessor(originalIP);
            int[] xpoints = new int[CenterObjects.size()];
            int[] ypoints = new int[CenterObjects.size()];
            for(int i = 0; i < CenterObjects.size(); i++){
                xpoints[i] = CenterObjects.get(i).x;
                ypoints[i] = CenterObjects.get(i).y;
            }
            Roi roi = new PointRoi(xpoints, ypoints, CenterObjects.size());
            roi.setStrokeColor(Color.green);
            Overlay overlay = new Overlay(roi);
            imp.setOverlay(overlay);

            for (int i = 0; i < CenterObjects.size(); i++){
                int leftEnd = 0;
                int rightEnd = 0;
                int tempX = CenterObjects.get(i).x;
                int tempY = CenterObjects.get(i).y;
                int tempCount = 0;
                int result = 0;
                //left
                do{
                    result =originalIP.getPixel(tempX,tempY);
                    if(result != 0){
                        leftEnd = tempX;
                    }
                    tempX--;
                }while(result != 0);

                //right
                tempX = CenterObjects.get(i).x;
                do{
                    result =originalIP.getPixel(tempX,tempY);
                    if(result != 0){
                        rightEnd = tempX;
                    }
                    tempX++;
                }while(result != 0);
                IJ.log("Size "+String.valueOf(i+1)+" object: "+String.valueOf(rightEnd-leftEnd));
            }
            */
    }
    public int calculateObjectSize(ImageProcessor ip){
        ImageProcessor TempIP = ip.duplicate();
        TempIP.findEdges();
        
        int maxSize = 0;
        int fX = 0;
        int fY = 0;
        int sX = 0;
        int sY = 0;
        int dX = 0;
        for(int y = 0; y < TempIP.getHeight(); y++){
            for(int x = 0; x < TempIP.getWidth(); x++){
                if((TempIP.getPixel(x-1, y) < 255) && (TempIP.getPixel(x, y) == 255)){
                    fX = x;  fY = y;
                    
                }
                
                if((TempIP.getPixel(x-1, y) == 255) && (TempIP.getPixel(x, y) < 255)){
                    sX = x; sY = y;
                    if((sX - fX)>5){
                        //IJ.log("1st: x="+String.valueOf(fX)+" y="+String.valueOf(fY));
                        //IJ.log("2nd: x="+String.valueOf(sX)+" y="+String.valueOf(sY));
                        dX = sX-fX;
                        //IJ.log("dX="+String.valueOf(dX));
                        if(maxSize < (dX)){
                            maxSize = dX;
                        }
                    }
                }
                        
            }
        }
        
        //ArrayToTextImage(TempIP.getIntArray(), "TempIP.txt");
        
        
        /*
        int width = (ip.getWidth()/mask)*mask;
        int height = (ip.getHeight()/mask)*mask;
        int avgSummary = 0;
        int maskSummary = 0;
        int counter = 0;
        for(int i = 0; i <= width; i +=mask){
            for(int j = 0; j <= height; j += mask){
                maskSummary = 0;
                counter++;
                for(int k = i; k <= i+mask; k++){
                    for(int l = j; l <= j+mask; l++){
                        maskSummary += ip.getPixel(k, l);
                    }
                }
                maskSummary /= mask*mask;
                //avgSummary += maskSummary;
                if(avgSummary < maskSummary){
                    avgSummary = maskSummary;
                }
            }
        }
        */
        return maxSize;
    }
    
    public int getSqueueObject(ImageProcessor ip, Point centerP){
        //Point centerP = new Point();
        //IJ.log("Center point = "+centerP.toString());
            //centerP.setLocation(k, k);
            //centerP = centerObject.get(k);
            int wile = -1;
            //left X
            int x1 = centerP.x;
            int x2 = centerP.x - 1;
            int dx = ip.getPixel(x1, centerP.y) - ip.getPixel(x2, centerP.y); //x1 - x2;
            while((dx >= wile)/*|(x2<0)*/){  
                x1 = x2;
                x2 = x2 - 1;
                dx = ip.getPixel(x1, centerP.y) - ip.getPixel(x2, centerP.y);
            }
            IJ.log("End left X  = "+String.valueOf(x2)+"   dx = "+String.valueOf(dx));
            int _x = x2;
            
            //right X
            x1 = centerP.x;
            x2 = centerP.x + 1;
            dx = ip.getPixel(x1, centerP.y) - ip.getPixel(x2, centerP.y);
            while((dx >= wile)/*|(x2>=ip.getWidth())*/){  
                x1 = x2;
                x2 = x2 + 1;
                dx = ip.getPixel(x1, centerP.y) - ip.getPixel(x2, centerP.y);
            }
            IJ.log("End right X = "+String.valueOf(x2)+"   dx = "+String.valueOf(dx));
            int _w = x2 - _x;
            
            //top y
            int y1 = centerP.y;
            int y2 = centerP.y - 1;
            int dy = ip.getPixel(centerP.x,y1 ) - ip.getPixel(centerP.x, y2);
            while((dy >= wile)/*|(y2<0)*/){  
                y1 = y2;
                y2 = y2 - 1;
                dy = ip.getPixel(centerP.x,y1 ) - ip.getPixel(centerP.x, y2);
            }
            IJ.log("End top Y = "+String.valueOf(y2)+"   dy = "+String.valueOf(dy));
            int _y = y2;
            
            //foot y
            y1 = centerP.y;
            y2 = centerP.y + 1;
            dy = ip.getPixel(centerP.x,y1 ) - ip.getPixel(centerP.x, y2);
            while((dy >= wile)/*|(y2<=ip.getHeight())*/){  
                y1 = y2;
                y2 = y2 + 1;
                dy = ip.getPixel(centerP.x,y1 ) - ip.getPixel(centerP.x, y2);
            }
            IJ.log("End foot Y = "+String.valueOf(y2)+"   dy = "+String.valueOf(dy));
            int _h = y2 - _y;
            
            //ip.drawOval(_x, _y, _w, _h);
        
        return _w*_h;
    }
    
    public ArrayList<Point> getMaximums(ArrayList<ArrayList<Point>> A, ImageProcessor originalIP){
        ArrayList<Point> aMaximums = new ArrayList<>();
        
        ArrayList<Point> tArray = new ArrayList<>();
            
        for(int k = 0; k < A.size(); k++){
            
            tArray = A.get(k);
            int minX = 10000000; int maxX = 0;
            int minY = 10000000; int maxY = 0;
            int x; int y;

            for(int i = 0; i < tArray.size(); i++){
                x = tArray.get(i).x;
                y = tArray.get(i).y;
                if(x > maxX){
                    maxX = x;
                }
                if(y > maxY){
                    maxY = y;
                }
                if(x < minX){
                    minX = x;
                }
                if(y < minY){
                    minY = y;
                }
            }

            IJ.log("minX = "+String.valueOf(minX));
            IJ.log("minY = "+String.valueOf(minY));
            IJ.log("maxX = "+String.valueOf(maxX));
            IJ.log("maxY = "+String.valueOf(maxY));

            int field[][] = new int[maxX-minX+1][maxY-minY+1];
            Point p = new Point();
            for(int i = 0; i < tArray.size(); i++){
                p = tArray.get(i);
                field[p.x-minX][p.y-minY] = originalIP.getPixel(p.x, p.y);
            }

            //ArrayToTextImage(field, "field.txt");
            
            for(int j=1; j<maxY-minY;j++){
                for(int i=1; i<maxX-minX; i++){
                    if(
                           (field[i][j] > field[i-1][j-1])
                        && (field[i][j] > field[i][j-1])
                        && (field[i][j] > field[i+1][j-1])
                        && (field[i][j] > field[i-1][j])
                        && (field[i][j] > field[i+1][j])
                        && (field[i][j] > field[i-1][j+1])
                        && (field[i][j] > field[i][j+1])
                        && (field[i][j] > field[i+1][j+1])
                      ){
                        aMaximums.add(new Point(i+minX,j+minY));
                    }
                    /*
                    int sum = 0;
                    sum = field[i-1][j-1] + field[i][j-1] + field[i+1][j-1] + field[i-1][j] + field[i+1][j] + field[i-1][j+1] + field[i][j+1] + field[i+1][j+1];
                    sum /= 8;
                    if(field[i][j] >= sum){
                        aMaximums.add(new Point(i+minX,j+minY));
                    }*/
                }
            }
            
        }
        return aMaximums;
    }
    
    public ArrayList<Point> getMaximums2(ArrayList<ArrayList<Point>> A, ImageProcessor originalIP){
        //ArrayList<ArrayList<Point>> squeuePoints = new ArrayList<>();
        ArrayList<Point> aMaximums = new ArrayList<>();
        ImageProcessor tIP;
        ArrayList<Point> tArray = new ArrayList<>();
            
        for(int k = 0; k < A.size(); k++){
            
            tArray = A.get(k);
            int minX = 10000000; int maxX = 0;
            int minY = 10000000; int maxY = 0;
            int x; int y;

            for(int i = 0; i < tArray.size(); i++){
                x = tArray.get(i).x;
                y = tArray.get(i).y;
                if(x > maxX){
                    maxX = x;
                }
                if(y > maxY){
                    maxY = y;
                }
                if(x < minX){
                    minX = x;
                }
                if(y < minY){
                    minY = y;
                }
            }

            //IJ.log("minX = "+String.valueOf(minX));
            //IJ.log("minY = "+String.valueOf(minY));
            //IJ.log("maxX = "+String.valueOf(maxX));
            //IJ.log("maxY = "+String.valueOf(maxY));
            
            tIP = originalIP.createProcessor(maxX-minX+1, maxY-minY+1);
            Point p = new Point();
            for(int i = 0; i < tArray.size(); i++){
                p = tArray.get(i);
                tIP.putPixel(p.x-minX, p.y-minY, originalIP.getPixel(p.x, p.y));               
            }
            
            
            
            MaximumFinder MF = new MaximumFinder();
            ByteProcessor bp = MF.findMaxima(tIP, 3, ImageProcessor.NO_THRESHOLD, 0, false, false);
            //ArrayToTextImage(tIP.getIntArray(), "tIP_"+String.valueOf(k)+".txt");
            for(int j=1; j<bp.getHeight();j++){
                for(int i=1; i<bp.getWidth(); i++){
                    if(bp.getPixel(i, j) == 255){
                        aMaximums.add(new Point(i+minX,j+minY));
                        //IJ.log(String.valueOf(getSqueueObject(tIP,new Point(i,j))));
                    }
                }
            }           
            
        }
        return aMaximums;
    }
    
    
    
    public ArrayList<Point> getChainCodeOneObject(ImageProcessor ip, int x , int y){
        ArrayList<Point> a = new ArrayList<>();
        a.add(new Point(x,y));
        Point dp = new Point(-1,0);
        
        if(ip.getPixel(x, y) == 255){
            a.add(new Point(x,y));
        }
        
        return a;
    }
    
    public Point nextPoint(Point p){
        Point table[] = {new Point(1,-1),
                         new Point(1,0),
                         new Point(1,1),
                         new Point(0,1),
                         new Point(-1,1),
                         new Point(-1,0),
                         new Point(-1,-1),
                         new Point(0,-1),
        };
        Point result = new Point();
        for(int i = 0; i <= 7; i++){
            if(p.equals(table[i])){
                if(i != 7){
                    result = table[i+1];
                } else {
                    result = table[0];
                }
            }
        }
        return result;
    }
    
    class ObjectData {
        
        private ArrayList<ArrayList<Point>> ChainCode = new ArrayList<ArrayList<Point>>();

        public int size(){
            return ChainCode.size();
        }
        
        public void addChainCodeObject(ArrayList<Point> newChainCodeObject){
            ChainCode.add(newChainCodeObject);
        }

        public ArrayList<Point> getChainCodeObject(int index){
            return ChainCode.get(index);
        }
        
        public ArrayList<Point> getPolarCooObject(int Index){
            return null;
        }
        
        private int getMaxX(int Index){
            int size;
            int max;
            ArrayList<Point> arrayPoint = getChainCodeObject(Index);
            ArrayList<Integer> arrayX = new ArrayList<>();
            size = arrayPoint.size();
            
            for (int i = 0; i < size; i++){
                arrayX.add(arrayPoint.get(i).x);
            }
            max = Collections.max(arrayX);
            return max;
        }
        
        private int getMaxY(int Index){
            int size;
            int max;
            ArrayList<Point> arrayPoint = getChainCodeObject(Index);
            ArrayList<Integer> arrayY = new ArrayList<>();
            size = arrayPoint.size();
            
            for (int i = 0; i < size; i++){
                arrayY.add(arrayPoint.get(i).y);
            }
            max = Collections.max(arrayY);
            return max;
        }
        
        private int getMinX(int Index){
            int size;
            int min;
            ArrayList<Point> arrayPoint = getChainCodeObject(Index);
            ArrayList<Integer> arrayX = new ArrayList<>();
            size = arrayPoint.size();
            
            for (int i = 0; i < size; i++){
                arrayX.add(arrayPoint.get(i).x);
            }
            min = Collections.min(arrayX);
            return min;
        }
        
        private int getMinY(int Index){
            int size;
            int min;
            ArrayList<Point> arrayPoint = getChainCodeObject(Index);
            ArrayList<Integer> arrayY = new ArrayList<>();
            size = arrayPoint.size();
            
            for (int i = 0; i < size; i++){
                arrayY.add(arrayPoint.get(i).y);
            }
            min = Collections.min(arrayY);
            return min;
        }
        
        private Point getCenterXY(int Index){
            
            int maxX = getMaxX(Index);
            int minX = getMinX(Index);
            int x = ((maxX-minX)/2)+minX;
            
            int maxY = getMaxY(Index);
            int minY = getMinY(Index);
            int y = ((maxY-minY)/2)+minY;
            return new Point(x,y);
        }
        
        public ArrayList<Point> getArrayCenterObjects(){
            ArrayList<Point> arrayCenterPoints = new ArrayList<>();
            ArrayList<Point> arrayChainPoints = new ArrayList<>();
            
            int size = ChainCode.size();
            
            for(int i = 0; i < size; i++){
                arrayCenterPoints.add(getCenterXY(i));
                
            }
            
            return arrayCenterPoints;
        }
        
        public ArrayList<Point> getDerivativePolarCoo(int Index){
        
            ArrayList<Point> arrayPoint = getChainCodeObject(Index);
            ArrayList<Point> arrayDerivativePoint = new ArrayList<>();
            Point centralPoint = getCenterXY(Index);
            int size = arrayPoint.size();
            int X;
            int Y;
            int Der;
            
            
            for (int i = 0; i < size; i++){
                X = arrayPoint.get(i).x - centralPoint.x;
                Y = arrayPoint.get(i).y - centralPoint.y;
                Der = (int)Math.sqrt(X*X+Y*Y);
                arrayDerivativePoint.add(new Point(i, Der));
            }
            
            return arrayDerivativePoint;
        }
    }
    
    private static class Vector{
            private static Point[] index = {
                            new Point(0,-1), 
                            new Point(1,-1),
                            new Point(1,0), 
                            new Point(1,1),
                            new Point(0,1), 
                            new Point(-1,1),
                            new Point(-1,0), 
                            new Point(-1,-1)
            };
            
            public static Point getVector(int i){
                
                return index[i];
            }
            
            public static Point inverseVector(Point p){
                p.x = p.x *(-1);
                p.y = p.y *(-1);
                return p;
            }
            
            public static boolean equals(Point p1, Point p2){
                if (p1.x == p2.x && p1.y == p2.y){
                    return true;
                } else {
                    return false;
                }
            }
            
            public static int getIndex(Point p){
                int ind = -1;
                for (int i = 0; i < index.length; i++){
                    if ((p.x == index[i].x) && (p.y == index[i].y) ){
                        ind = i;
                    }
                }
                return ind;
            }
            
            public static Point nextVector(Point p){
                int i = getIndex(p);
                if (i == 7){
                    i = 0;
                    return getVector(i);
                } else {
                    i += 1;
                    return getVector(i);
                }
            }
        }
    
    public ArrayList<Point> getSquereObject(ArrayList<Point> a){
        int X;
        ArrayList<Point> SQ = new ArrayList<>();
        ArrayList<Point> temp = new ArrayList<>();
        for (int i = 0; i < a.size(); i++){
            X = a.get(i).x;
            
            //Ищем все поинты равные Х
            for (int j = 0; j < a.size(); j++){
                if (a.get(j).x == X) {
                    temp.add(a.get(j));
                }
            }
            
            //Ищем max поинт из найденных
            int Y = a.get(i).y;
            //int maxIndex;
            for (int j = 0; j < temp.size(); j++){
                if (temp.get(j).y > Y){
                    Y = temp.get(j).y;
                    //maxIndex = j;
                }
            }
            temp.clear();
            for (int j = a.get(i).y; j <= Y; j++){
                SQ.add(new Point(a.get(i).x, j));
            }
        }
        
        
        //IJ.log(SQ.toString());
        
        //for (int i = 0; i < SQ.size(); i++){
          //  ip.putPixel(SQ.get(i).x, SQ.get(i).y, 150);
        //}
        return SQ;
    }
    
    public ArrayList<Point> getArrayObject(ImageProcessor ip, int x, int y){
        Point v = new Point(1,0);
        Point pixel = new Point(x,y);
        Point nextpixel = new Point(1,1);    
        ArrayList<Point> array = new ArrayList<>();
        array.add(pixel);
        
        while (!nextpixel.equals(new Point(x, y))) {
            //IJ.log("while: next="+nextpixel+" xy="+new Point(x,y));
            nextpixel = getNextPixel(ip, pixel.x, pixel.y, v);
            v = getNextVector(nextpixel, pixel);
            //IJ.log(" "+pixel+" "+nextpixel+""+v);
            pixel = nextpixel;
            array.add(pixel);
        }
        
        return array;
    }
    
    public ArrayList<ArrayList<Point>> getArrayPixelsObject(){
        ArrayList<ArrayList<Point>> MainArray = new ArrayList<>();
        ArrayList<Point>                Array = new ArrayList<>();
        Array.add(new Point(2,4));
        MainArray.add(Array);
        
        return MainArray;
    }
    
    public Point getNextVector(Point p1, Point p2){
        Point p = new Point();
        p.x = p1.x - p2.x;
        p.y = p1.y - p2.y;
        return p;
    }
    
    public Point getNextPixel(ImageProcessor ip, int x, int y, Point v){
        v = Vector.inverseVector(v);
        do{
            v = Vector.nextVector(v);
            
        }while(ip.getPixel(x+v.x, y+v.y) != 255);
        return new Point(x+v.x,y+v.y);
    }
    
    
    
    
    
    
    public boolean isNormalIntensityWeight(ImageProcessor ip){
        int width = ip.getWidth();
        int heght = ip.getHeight();

        int avg = 0;
        int max = 0;
        int count = 0;
        int pixel = 0;
        
        for(int y = 0; y < heght; y++){
            for(int x = 0; x < width; x++){
                count++;
                pixel = ip.getPixel(x, y);
                if(pixel > max){
                    max = pixel;
                }
                avg += pixel;
            }
        }
        
        avg = (int)avg/count;

            IJ.log("max = "+String.valueOf(max));
            IJ.log("avg = "+String.valueOf(avg));
        
        if(max > avg){
            return true;
        } else {
            return false;
        }
    }
    
    public ImageProcessor makeBinaryIp(ImageProcessor ip, int threshold){
        int width = ip.getWidth();
        int heght = ip.getHeight();
        
        //dublIP.
        
        for(int y = 0; y < heght; y++){
            for(int x = 0; x < width; x++){
                if(ip.getPixel(x, y)>threshold){
                    ip.putPixel(x, y, 255);
                } else {
                    ip.putPixel(x, y, 0);
                }
            }
        }
        return ip;
    }
    
    public int getAvgOnSquare(ImageProcessor ip, int sq){
        int min;
        int max;
        int maxsq = 0;
        int count = 0;
        for(int y = 0; y < ip.getHeight() - sq; y++){
            for(int x = 0; x < ip.getWidth() - sq; x++){
                /*
                if(ip.getPixel(x, y) > max){
                    max = ip.getPixel(x, y);
                }
                
                if(ip.getPixel(x, y) < min){
                    min = ip.getPixel(x, y);
                }*/
                count += 1;
                min = 255;
                max = 0;
                for (int _y = y; _y < y + sq; _y++ ){
                    for (int _x = x; _x < x + sq; _x++ ){
                        if (ip.getPixel(_x, _y) > max){
                            max = ip.getPixel(_x, _y);
                        }
                        if (ip.getPixel(_x, _y) < min){
                            min = ip.getPixel(_x, _y);
                        }
                    }
                }
                maxsq += max-min;
                
            }
        }
        maxsq /= count; 
        return maxsq;
    }
    
    public int getAvgOnSquare2(ImageProcessor ip, int sq, int th){
        int count = 0;
        int c = 0;
        int avgcount=0;
        int histogramm[] = new int[256];
        for(int y = 0; y < ip.getHeight(); y++){
            for(int x = 0; x < ip.getWidth(); x++){
                
                for(int _y = y; _y < y+sq; _y++){
                    for(int _x = x; _x < x+sq; _x++){
                        histogramm[ip.getPixel(_x, _y)] += 1;
                    }
                }
                for(int i = 0; i <= 255; i++){
                    if(histogramm[i] > th){
                        count++;
                    }
                }
                avgcount += count;
                c++;
                count = 0;
                for(int i=0; i<256;i++){
                    histogramm[i]=0;
                }
            }
        }
            //IJ.log("c="+String.valueOf(c));
            //IJ.log("="+String.valueOf(c));
        return (int)avgcount/c;
    }
    
    public ImageProcessor VariationalRange(ImageProcessor ip, int range, int threshold){
        ImageProcessor dip = ip.duplicate();
      
        int min;
        for(int y = 0; y < ip.getHeight() - range; y = y+ range){
            for(int x = 0; x < ip.getWidth() - range; x = x +range){
                
                min = 255;
                for (int _y = y; _y < y + range; _y++ ){
                    for (int _x = x; _x < x + range; _x++ ){
                        if(ip.getPixel(_x, _y) < min){
                            min = ip.getPixel(_x, _y);
                        }
                    }
                }
                for (int _y = y; _y < y + range; _y++ ){
                    for (int _x = x; _x < x + range; _x++ ){
                        dip.putPixel(_x, _y, min + threshold);
                    }
                }
            }
        }
        return dip;
    }
    
    public ImageProcessor VariationalRange(ImageProcessor ip, int range){
        ImageProcessor dip = ip.duplicate();
        
        int prefix = range / 2;
        int field[][] = new int[ip.getWidth()][ip.getHeight()];
        
        int min;
        int max;
        for(int y = prefix; y < ip.getHeight() - prefix; y++){
            for(int x = prefix; x < ip.getWidth() - prefix; x++){
                min = 255;
                max = 0;
                for (int _y = y - prefix; _y < y + prefix; _y++ ){
                    for (int _x = x - prefix; _x < x + prefix; _x++ ){
                        if (ip.getPixel(_x, _y) > max){
                            max = ip.getPixel(_x, _y);
                        }
                        if (ip.getPixel(_x, _y) < min){
                            min = ip.getPixel(_x, _y);
                        }
                    }
                }
                for (int _y = y - prefix; _y < y + prefix; _y++ ){
                    for (int _x = x - prefix; _x < x + prefix; _x++ ){
                        field[_x][_y] = max - min;
                    }
                }
            }
        }
        
        max = 0;
        for(int y = prefix; y < ip.getHeight() - prefix; y++){
            for(int x = prefix; x < ip.getWidth() - prefix; x++){
                if (field[x][y] > max) {
                    max = field[x][y];
                }
            }
        }
        
        long k = 255/max;
        
        for(int y = prefix; y < ip.getHeight() - prefix; y++){
            for(int x = prefix; x < ip.getWidth() - prefix; x++){
                field[x][y] *= k;
            }
        }
        dip.setIntArray(field);
        return dip;
    }
    
    public ImageProcessor SubstructImages(ImageProcessor ip1, ImageProcessor ip2){
        int width = ip1.getWidth();
        int height = ip2.getHeight();
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                ip1.putPixel(x, y,  (ip1.getPixel(x, y)-ip2.getPixel(x, y))  );
            }
        }
        return ip1;
    }
        
    public void ImageProcessorToJpg(ImageProcessor ip, int i){
        ImageProcessor dublIp = ip.duplicate();
        String prefix = "";
        if ((i >= 0)&&(i < 9)){
            prefix = "00";
        }
        if ((i >= 10)&&(i < 99)) {
            prefix = "0";
        }
        FileSaver fs = new FileSaver(new ImagePlus(null, dublIp));
        fs.saveAsJpeg("bmp/"+prefix  +i+".jpg");
    }
    
    public void ImageProcessorToJpg(ImageProcessor ip){
        ImageProcessor dublIp = ip.duplicate();
        int width = ip.getWidth();
        int height = ip.getHeight();
        String prefix = "";
        
        File dir = new File("bmp");
        if (!dir.exists()) {
            if (dir.mkdir()){
                for(int i = 0; i <=255; i++){
                    if ((i >= 0)&&(i < 9)){
                        prefix = "00";
                    }
                    if ((i >= 10)&&(i < 99)) {
                        prefix = "0";
                    }
                    
                    dublIp = ip.duplicate();
                    for(int y = 0; y < height; y++){
                        for(int x = 0; x < width; x++){
                            if(dublIp.getPixel(x, y) >= i){
                                dublIp.putPixel(x, y, 255);
                            } else {
                                dublIp.putPixel(x, y, 0);
                            }
                        }
                    }
                    
                    FileSaver fs = new FileSaver(new ImagePlus(null, dublIp));
                    fs.saveAsJpeg("bmp/"+prefix  +i+".jpg");
                    prefix = "";
                } 
            } else {
                IJ.log("ERROR: Directory has not been create!");
            }
        } else {
            for(int i = 0; i <=255; i++){
                    if ((i >= 0)&&(i <= 9)){
                        prefix = "00";
                    }
                    if ((i >= 10)&&(i <= 99)) {
                        prefix = "0";
                    }
                    
                    dublIp = ip.duplicate();
                    for(int y = 0; y < height; y++){
                        for(int x = 0; x < width; x++){
                            if(dublIp.getPixel(x, y) >= i){
                                dublIp.putPixel(x, y, 255);
                            } else {
                                dublIp.putPixel(x, y, 0);
                            }
                        }
                    }
                    
                    FileSaver fs = new FileSaver(new ImagePlus(null, dublIp));
                    fs.saveAsJpeg("bmp/"+prefix  +i+".jpg");
                    prefix = "";
                }
        }
        
    }
    
    public void ArrayToTextImage(int array[][], String textfile){
        try{
            // Create file 
            FileWriter fw = new FileWriter(textfile);
            BufferedWriter out = new BufferedWriter(fw);
            int pixel;



            for (int j = 0; j <= array[0].length - 1; j++)
            {
                for (int i = 0; i <= array.length -1;i++)
                {
                    pixel = array[i][j];
                    out.write(String.valueOf(pixel));
                    out.write("\t");
                }
                out.write("\n");
            }
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
  
            
    }
    
    public void ArrayToTextImage(int array[], String textfile){
        try{
            // Create file 
            FileWriter fw = new FileWriter(textfile);
            BufferedWriter out = new BufferedWriter(fw);
            int pixel;



            
                for (int i = 0; i <= array.length -1;i++)
                {
                    pixel = array[i];
                    out.write(String.valueOf(pixel));
                    out.write("\t");
                    out.write("\n");
                }
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
  
            
    }
}
