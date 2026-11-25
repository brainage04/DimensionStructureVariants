import unittest

from step_check import FRACTION, ratios, repeated

# Steps of take 14's film around the repeated frame of stage 8's palette half (film pairs 932 to 937,
# measured by cut_film.py's metric), and a half whose steps start slow and grow with the descent.
SNOWY_AROUND_THE_REPEAT = [26.17, 28.47, 30.17, 0.35, 30.94, 30.67, 31.2, 30.9, 31.5, 32.0]
SLOW_START = [1.10, 1.4, 2.0, 2.6, 3.5, 4.4, 5.6, 7.0, 9.5, 12.0, 15.0, 19.0, 24.0, 30.0]


class StepCheckTest(unittest.TestCase):
    def test_the_repeated_frame_is_found(self):
        self.assertEqual([3], repeated(SNOWY_AROUND_THE_REPEAT))

    def test_a_slow_start_is_not_a_repeat(self):
        self.assertEqual([], repeated(SLOW_START))
        self.assertGreater(min(ratios(SLOW_START)), FRACTION)

    def test_a_half_without_neighbours_is_not_flagged(self):
        self.assertEqual([], repeated([5.0]))


if __name__ == "__main__":
    unittest.main()
