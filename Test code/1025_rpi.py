import pandas as pd 
from imutils.video import WebcamVideoStream
import imutils
import matplotlib.pyplot as plt
import cv2
import numpy as np
import threading
import mediapipe as mp
from matplotlib import pyplot as plt
import time
from imutils.video import FPS
import math
import serial
from time import sleep
import sys
import glob
import serial
from collections import deque
import numpy as np
import struct
import threading
import json
import array
import random

ser = serial.Serial(      #create serial 
    port = "/dev/ttyS0", #portname
    baudrate = 115200,    #communication speed
    parity = serial.PARITY_NONE, #parity
    stopbits = serial.STOPBITS_ONE, #stopbits
    bytesize = serial.EIGHTBITS, #the number of data byte
    timeout = 0  #config timeout
    )




STRECTHING = [[1,6],[2,4],[3,2],[4,6]] 

def scorelist(num):
    
    scores = [random.randrange(70,101) for i in range(STRECTHING[num-1][1])]
    
    #print(scores)
    
    return scores
    
def send_often(num):
    
    data_o ={}
    data_o["score"]= scorelist(num)
    
    data_o["direction"]= random.randrange(0,2)
    data_o["reps"]= random.randrange(0,2)
    data = json.dumps(data_o)
    
    data = (bytearray(data,"ascii"))
    length = len(data)
    print('length:',length,'data:',data)
    ser.write(bytearray(struct.pack("i", length)))
    time.sleep(0.1)
    ser.write(data)
    

    

for i in range(5):
    send_often(3)
    time.sleep(0.2)
    
print("Done")





