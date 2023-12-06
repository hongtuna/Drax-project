from threading import Thread
import cv2

class WebcamVideoStream:
    def __init__(self,src=-1):
						# camera를 시작시킨다.
            self.stream = cv2.VideoCapture(-1)
            (self.grabbed, self.frame) = self.stream.read()

            self.stopped = False

    def start(self):
			      # 스레드를 이용해서 시작
            Thread(target=self.update,args=()).start()
            return self

    def update(self):
						# 프레임 업데이트
            while True:
                    if self.stopped:
                            return
                    (self.grabbed,self.frame) = self.stream.read()
    def read(self):
			      # 프레임 반환
            return self.frame
    def stop(self):
            self.stopped = True