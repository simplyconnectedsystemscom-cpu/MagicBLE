from PIL import Image, ImageDraw, ImageFont
import random

# Create a white image
width = 400
height = 300
img = Image.new('RGB', (width, height), color='white')
draw = ImageDraw.Draw(img)

# Draw a simulated barcode
# Barcode area
bar_height = 150
start_y = (height - bar_height) // 2
start_x = 50
end_x = width - 50

current_x = start_x
while current_x < end_x:
    # Random bar width: 1 to 4 pixels
    bar_width = random.randint(1, 4)
    # Random gap: 1 to 4 pixels
    gap_width = random.randint(1, 4)
    
    if current_x + bar_width > end_x:
        break
        
    # Draw bar (black)
    draw.rectangle([current_x, start_y, current_x + bar_width, start_y + bar_height], fill='black')
    
    current_x += bar_width + gap_width

# Add Text "MagicBLE"
try:
    # Try to load a font, otherwise use default
    font = ImageFont.truetype("arial.ttf", 30)
except IOError:
    font = ImageFont.load_default()

text = "MagicBLE Cart"
# Calculate text width/height
bbox = draw.textbbox((0, 0), text, font=font)
text_width = bbox[2] - bbox[0]
text_height = bbox[3] - bbox[1]

text_x = (width - text_width) // 2
text_y = start_y + bar_height + 10

# Draw text (black)
draw.text((text_x, text_y), text, fill='black', font=font)

# Save
img.save("magic_barcode.png")
print("Barcode image generated: magic_barcode.png")
