import datetime

class FPS:
	def __init__(self):
		# 시작시간,종료시간,프레임수
		self._start = None
		self._end = None
		self._numFrames = 0

	def start(self):
		# 시작
		self._start = datetime.datetime.now()
		return self

	def stop(self):
		# 종료
		self._end = datetime.datetime.now()

	def update(self):
		# 프레임 업데이트
		self._numFrames += 1

	def elapsed(self):
		# 총 걸린시간 반환
		return (self._end - self._start).total_seconds()

	def fps(self):
		# FPS 반환
		return self._numFrames / self.elapsed()
	

